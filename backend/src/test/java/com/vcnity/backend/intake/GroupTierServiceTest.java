package com.vcnity.backend.intake;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Group-level tiering rules agreed with the team (tier lives on the group, set only by a person). */
class GroupTierServiceTest {

    private final InMemoryGroupTierStore store = new InMemoryGroupTierStore();
    private final GroupTierService service = new GroupTierService(store);

    @Test
    void groupWithNoRecordIsUnclassifiedAndHasNoDefaultTier() {
        GroupTierService.TierResolution resolution = service.resolve(List.of("youth group a"));

        assertFalse(resolution.isClassified());
        assertNull(resolution.tier());
        assertEquals(List.of("youth group a"), resolution.unclassifiedGroupIds());
    }

    @Test
    void settingATierRecordsWhoWhenAndWhy() {
        GroupTier saved = service.setTier("Youth Group A", null, 2, "Mohika", "Personal stories are shared here");

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
        service.setTier("elders circle", null, 1, "Mohika", "General feedback only");
        service.setTier("elders circle", null, 3, "Chloee", "Cultural knowledge is discussed");

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
        assertThrows(IllegalArgumentException.class, () -> service.setTier("g", null, 2, "  ", "reason"));
        assertThrows(IllegalArgumentException.class, () -> service.setTier("g", null, 2, null, "reason"));
        assertThrows(IllegalArgumentException.class, () -> service.setTier("g", null, 2, "Mohika", " "));
        assertThrows(IllegalArgumentException.class, () -> service.setTier("g", null, 2, "Mohika", "x".repeat(501)));
        assertTrue(service.get("g").isEmpty(), "a refused change must not create a record");
    }

    @Test
    void onlyTiersOneTwoAndThreeAreAccepted() {
        assertThrows(IllegalArgumentException.class, () -> service.setTier("g", null, 0, "Mohika", "reason"));
        assertThrows(IllegalArgumentException.class, () -> service.setTier("g", null, 4, "Mohika", "reason"));
        assertThrows(IllegalArgumentException.class, () -> service.setTier("g", null, null, "Mohika", "reason"));
        assertThrows(IllegalArgumentException.class, () -> service.setTier("   ", null, 1, "Mohika", "reason"));
        assertTrue(service.list().isEmpty());
    }

    @Test
    void groupIdsMatchRegardlessOfCaseAndExtraSpaces() {
        service.setTier("Youth Group A", null, 1, "Mohika", "General feedback only");

        assertTrue(service.resolve(List.of("  youth   GROUP a ")).isClassified());
        assertEquals(1, service.list().size());
    }

    @Test
    void submissionToSeveralGroupsTakesTheMostRestrictiveTier() {
        service.setTier("a", null, 1, "Mohika", "General");
        service.setTier("b", null, 2, "Mohika", "Sensitive");
        service.setTier("c", null, 3, "Mohika", "Restricted");

        assertEquals(2, service.resolve(List.of("a", "b")).tier());
        assertEquals(3, service.resolve(List.of("a", "c", "b")).tier());
        assertEquals(1, service.resolve(List.of("a")).tier());
    }

    @Test
    void oneUnclassifiedGroupMakesTheWholeSubmissionUnclassified() {
        service.setTier("a", null, 1, "Mohika", "General");

        GroupTierService.TierResolution resolution = service.resolve(List.of("a", "new group"));

        assertFalse(resolution.isClassified());
        assertNull(resolution.tier());
        assertEquals(List.of("new group"), resolution.unclassifiedGroupIds());
    }
}
