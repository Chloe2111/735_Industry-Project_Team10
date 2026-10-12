package com.vcnity.backend.intake;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Group-level tiering rules agreed with the team (tier lives on the group, set only by a person). */
class GroupTierServiceTest {

    private final InMemoryGroupTierStore store = new InMemoryGroupTierStore();
    private final GroupTierService service = new GroupTierService(store);

    /** Sets a tier the way a coordinator does: based on the version of the record they are looking at. */
    private GroupTier set(String groupId, Integer tier, String setBy, String reason) {
        Long seen = store.findById(GroupTierService.normaliseGroupId(groupId)).map(GroupTier::getVersion).orElse(null);
        return service.setTier(groupId, null, tier, setBy, reason, seen);
    }

    @Test
    void groupWithNoRecordIsUnclassifiedAndHasNoDefaultTier() {
        GroupTierService.TierResolution resolution = service.resolve(List.of("youth group a"));

        assertFalse(resolution.isClassified());
        assertNull(resolution.tier());
        assertEquals(List.of("youth group a"), resolution.unclassifiedGroupIds());
    }

    @Test
    void settingATierRecordsWhoWhenAndWhy() {
        GroupTier saved = set("Youth Group A", 2, "Mohika", "Personal stories are shared here");

        assertEquals("youth group a", saved.getGroupId());
        assertEquals("Youth Group A", saved.getGroupName());
        assertEquals(2, saved.getTier());
        assertEquals("Mohika", saved.getSetBy());
        assertEquals("Personal stories are shared here", saved.getReason());
        assertNotNull(saved.getSetAt());
        assertEquals(2, service.resolve(List.of("youth group a")).tier());
    }

    @Test
    void everyChangeIsKeptInTheHistory() {
        set("elders circle", 1, "Mohika", "General feedback only");
        set("elders circle", 3, "Chloee", "Cultural knowledge is discussed");

        GroupTier record = service.get("elders circle").orElseThrow();
        assertEquals(3, record.getTier());
        assertEquals(2, record.getHistory().size());
        assertNull(record.getHistory().get(0).getFromTier());
        assertEquals(1, record.getHistory().get(0).getToTier());
        assertEquals("Mohika", record.getHistory().get(0).getSetBy());
        assertEquals(1, record.getHistory().get(1).getFromTier());
        assertEquals(3, record.getHistory().get(1).getToTier());
        assertEquals("Chloee", record.getHistory().get(1).getSetBy());
        assertEquals("Cultural knowledge is discussed", record.getHistory().get(1).getReason());
    }

    @Test
    void aTierCannotBeSetWithoutAPersonAndAReason() {
        assertThrows(IllegalArgumentException.class, () -> set("g", 2, "  ", "reason"));
        assertThrows(IllegalArgumentException.class, () -> set("g", 2, null, "reason"));
        assertThrows(IllegalArgumentException.class, () -> set("g", 2, "Mohika", " "));
        assertThrows(IllegalArgumentException.class, () -> set("g", 2, "Mohika", "x".repeat(501)));
        assertTrue(service.get("g").isEmpty(), "a refused change must not create a record");
    }

    @Test
    void onlyTiersOneTwoAndThreeAreAccepted() {
        assertThrows(IllegalArgumentException.class, () -> set("g", 0, "Mohika", "reason"));
        assertThrows(IllegalArgumentException.class, () -> set("g", 4, "Mohika", "reason"));
        assertThrows(IllegalArgumentException.class, () -> set("g", null, "Mohika", "reason"));
        assertThrows(IllegalArgumentException.class, () -> set("   ", 1, "Mohika", "reason"));
        assertTrue(service.list().isEmpty());
    }

    @Test
    void groupIdsMatchRegardlessOfCaseAndExtraSpaces() {
        set("Youth Group A", 1, "Mohika", "General feedback only");

        assertTrue(service.resolve(List.of("  youth   GROUP a ")).isClassified());
        assertEquals(1, service.list().size());
    }

    @Test
    void submissionToSeveralGroupsTakesTheMostRestrictiveTier() {
        set("a", 1, "Mohika", "General");
        set("b", 2, "Mohika", "Sensitive");
        set("c", 3, "Mohika", "Restricted");

        assertEquals(2, service.resolve(List.of("a", "b")).tier());
        assertEquals(3, service.resolve(List.of("a", "c", "b")).tier());
        assertEquals(1, service.resolve(List.of("a")).tier());
    }

    @Test
    void oneUnclassifiedGroupMakesTheWholeSubmissionUnclassified() {
        set("a", 1, "Mohika", "General");

        GroupTierService.TierResolution resolution = service.resolve(List.of("a", "new group"));

        assertFalse(resolution.isClassified());
        assertNull(resolution.tier());
        assertEquals(List.of("new group"), resolution.unclassifiedGroupIds());
    }

    // --- Two coordinators changing the same group ---

    @Test
    void changeBasedOnAStaleViewIsRefusedInsteadOfOverwritingTheNewerOne() {
        set("youth group a", 1, "Mohika", "General feedback only");
        Long versionBothCoordinatorsSee = service.get("youth group a").orElseThrow().getVersion();

        // Chloee saves first.
        service.setTier("youth group a", null, 3, "Chloee", "Cultural knowledge is now shared", versionBothCoordinatorsSee);

        // Mohika still has the old page open and tries to save over it.
        ConcurrentTierChangeException refused = assertThrows(ConcurrentTierChangeException.class,
                () -> service.setTier("youth group a", null, 2, "Mohika", "More personal content", versionBothCoordinatorsSee));

        assertEquals(409, refused.getStatus());
        assertTrue(refused.getMessage().contains("Tier 3"), "the message says what the group is now");
        assertTrue(refused.getMessage().contains("Chloee"), "and who changed it");
        GroupTier record = service.get("youth group a").orElseThrow();
        assertEquals(3, record.getTier(), "Chloee's change must still be there");
        assertEquals("Chloee", record.getSetBy());
        assertEquals(2, record.getHistory().size(), "the refused change must not appear in the history");
    }

    @Test
    void twoPeopleClassifyingTheSameNewGroupOnlyTheFirstIsSaved() {
        service.setTier("new group", null, 3, "Chloee", "Restricted material", null);

        assertThrows(ConcurrentTierChangeException.class,
                () -> service.setTier("new group", null, 1, "Mohika", "General feedback", null));

        assertEquals(3, service.get("new group").orElseThrow().getTier());
        assertEquals(1, service.get("new group").orElseThrow().getHistory().size());
    }

    @Test
    void storeRefusesAWriteThatSlipsPastTheServiceCheck() {
        set("g", 1, "Mohika", "General");
        // Two requests read the record at the same instant, so both pass the service's version check...
        GroupTier firstCopy = store.findById("g").orElseThrow();
        GroupTier secondCopy = store.findById("g").orElseThrow();
        firstCopy.setTier(3);
        secondCopy.setTier(2);

        store.save(firstCopy);

        // ...but the store compares versions again at the moment of writing.
        assertThrows(ConcurrentTierChangeException.class, () -> store.save(secondCopy));
        assertEquals(3, store.findById("g").orElseThrow().getTier());
    }

    @Test
    void whenManyPeopleSaveAtTheSameMomentExactlyOneWins() throws Exception {
        set("busy group", 1, "Mohika", "General");
        Long seenByEveryone = service.get("busy group").orElseThrow().getVersion();
        int people = 12;
        CountDownLatch ready = new CountDownLatch(people);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger saved = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        List<Throwable> unexpected = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < people; i++) {
            int tier = 2 + (i % 2);
            String who = "Coordinator " + i;
            Thread thread = new Thread(() -> {
                ready.countDown();
                try {
                    go.await();
                    service.setTier("busy group", null, tier, who, "Simultaneous change", seenByEveryone);
                    saved.incrementAndGet();
                } catch (ConcurrentTierChangeException e) {
                    refused.incrementAndGet();
                } catch (Throwable t) {
                    synchronized (unexpected) {
                        unexpected.add(t);
                    }
                }
            });
            threads.add(thread);
            thread.start();
        }
        ready.await();
        go.countDown();
        for (Thread thread : threads) {
            thread.join();
        }

        assertTrue(unexpected.isEmpty(), "unexpected errors: " + unexpected);
        assertEquals(1, saved.get(), "exactly one simultaneous change may be saved");
        assertEquals(people - 1, refused.get());
        GroupTier record = service.get("busy group").orElseThrow();
        assertEquals(2, record.getHistory().size(), "history holds the first classification and the one winner");
        assertEquals(record.getHistory().get(1).getSetBy(), record.getSetBy());
        assertEquals(record.getHistory().get(1).getToTier(), record.getTier());
    }
}
