package com.vcnity.backend.intake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the two MongoDB stores against a real local MongoDB. Skipped unless asked for, like
 * MongoPersistenceIntegrationTest:
 *
 *   mvn -f backend/pom.xml -Dmongodb.integration=true -Dtest=MongoIntakeStoresIntegrationTest test
 *
 * Every record it writes uses a group id unique to this run and is removed afterwards.
 */
@DataMongoTest(properties = {
        "spring.data.mongodb.uri=mongodb://127.0.0.1:27017/"
                + "vcnity_story24_dev?serverSelectionTimeoutMS=5000",
        "spring.data.mongodb.database=vcnity_story24_dev"
})
@EnabledIfSystemProperty(named = "mongodb.integration", matches = "true")
class MongoIntakeStoresIntegrationTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    private MongoGroupTierStore tiers;
    private MongoSubmissionStore submissions;
    private String group;
    private String otherGroup;

    @BeforeEach
    void setUp() {
        tiers = new MongoGroupTierStore(mongoTemplate);
        submissions = new MongoSubmissionStore(mongoTemplate);
        String run = UUID.randomUUID().toString();
        group = "it-group-" + run;
        otherGroup = "it-other-" + run;
    }

    @AfterEach
    void removeWhatThisTestWrote() {
        mongoTemplate.remove(Query.query(Criteria.where("_id").in(List.of(group, otherGroup))), GroupTier.class);
        mongoTemplate.remove(Query.query(Criteria.where("groupIds").in(List.of(group, otherGroup))), Submission.class);
    }

    private GroupTier newTier(String groupId, int tier) {
        GroupTier record = new GroupTier();
        record.setGroupId(groupId);
        record.setGroupName("Integration test group");
        record.setTier(tier);
        record.setSetBy("Integration test");
        record.setReason("Synthetic record");
        record.setSetAt(Instant.now());
        record.getHistory().add(new GroupTier.TierChange(null, tier, "Integration test", "Synthetic record", Instant.now()));
        return record;
    }

    private Submission newSubmission(String groupId, SubmissionStatus status, Instant submittedAt) {
        Submission submission = new Submission();
        submission.setId(UUID.randomUUID().toString());
        submission.setGroupIds(List.of(groupId));
        submission.setText("Synthetic feedback.");
        submission.setTierAtIntake(1);
        submission.setStatus(status);
        submission.setFlags(List.of("lowConfidence"));
        submission.setSubmittedAt(submittedAt);
        submission.setUpdatedAt(submittedAt);
        return submissions.save(submission);
    }

    // --- Group tiers ---

    @Test
    void groupTierIsStoredWithItsHistoryAndAVersionThatCountsSaves() {
        GroupTier saved = tiers.save(newTier(group, 2));
        assertEquals(0L, saved.getVersion());

        GroupTier loaded = tiers.findById(group).orElseThrow();
        assertEquals(2, loaded.getTier());
        assertEquals("Integration test", loaded.getSetBy());
        assertEquals(1, loaded.getHistory().size());
        assertEquals(0L, loaded.getVersion());

        loaded.setTier(3);
        assertEquals(1L, tiers.save(loaded).getVersion());
        assertEquals(3, tiers.findById(group).orElseThrow().getTier());
    }

    @Test
    void staleWriteIsRefusedSoOneCoordinatorCannotOverwriteAnother() {
        tiers.save(newTier(group, 1));
        GroupTier firstCopy = tiers.findById(group).orElseThrow();
        GroupTier secondCopy = tiers.findById(group).orElseThrow();
        firstCopy.setTier(3);
        secondCopy.setTier(2);

        tiers.save(firstCopy);

        assertThrows(ConcurrentTierChangeException.class, () -> tiers.save(secondCopy));
        assertEquals(3, tiers.findById(group).orElseThrow().getTier());
    }

    @Test
    void twoFirstTimeClassificationsOfTheSameGroupOnlyTheFirstIsStored() {
        tiers.save(newTier(group, 3));

        assertThrows(ConcurrentTierChangeException.class, () -> tiers.save(newTier(group, 1)));
        assertEquals(3, tiers.findById(group).orElseThrow().getTier());
    }

    @Test
    void severalGroupsAreLoadedTogetherAndMissingOnesAreSimplyAbsent() {
        tiers.save(newTier(group, 1));
        tiers.save(newTier(otherGroup, 3));

        List<GroupTier> found = tiers.findByIds(List.of(group, otherGroup, "it-no-such-group"));

        assertEquals(2, found.size());
        assertTrue(tiers.findAll().stream().anyMatch(g -> g.getGroupId().equals(group)));
    }

    // --- Submissions ---

    @Test
    void submissionsAreFoundByGroupAndStatusNewestFirst() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Submission older = newSubmission(group, SubmissionStatus.PUBLISHED, now.minusSeconds(60));
        Submission newer = newSubmission(group, SubmissionStatus.PUBLISHED, now);
        newSubmission(group, SubmissionStatus.PENDING_REVIEW, now);
        newSubmission(otherGroup, SubmissionStatus.PUBLISHED, now);

        List<Submission> published = submissions.findByGroupIdAndStatus(group, SubmissionStatus.PUBLISHED);

        assertEquals(List.of(newer.getId(), older.getId()), published.stream().map(Submission::getId).toList());
        assertEquals(2, submissions.countByGroupIdAndStatus(group, SubmissionStatus.PUBLISHED));
        assertEquals("Synthetic feedback.", submissions.findById(newer.getId()).orElseThrow().getText());
        assertEquals(now, submissions.findById(newer.getId()).orElseThrow().getSubmittedAt());
        assertTrue(submissions.findByStatus(SubmissionStatus.PENDING_REVIEW).stream()
                .anyMatch(s -> s.getGroupIds().contains(group)));
    }

    @Test
    void statusChangesOnlyWhileTheSubmissionIsStillInTheExpectedStatus() {
        Submission waiting = newSubmission(group, SubmissionStatus.PENDING_REVIEW, Instant.now());

        assertFalse(submissions.changeStatus(waiting.getId(), SubmissionStatus.HELD, SubmissionStatus.PUBLISHED, null, Instant.now()));
        assertEquals(SubmissionStatus.PENDING_REVIEW, submissions.findById(waiting.getId()).orElseThrow().getStatus());

        assertTrue(submissions.changeStatus(waiting.getId(), SubmissionStatus.PENDING_REVIEW, SubmissionStatus.PUBLISHED, null, Instant.now()));
        assertEquals(SubmissionStatus.PUBLISHED, submissions.findById(waiting.getId()).orElseThrow().getStatus());
    }

    @Test
    void haltingAGroupHoldsItsWaitingAndRejectedSubmissionsAndReportsExactlyThose() {
        Instant now = Instant.now();
        Submission waitingA = newSubmission(group, SubmissionStatus.PENDING_REVIEW, now);
        Submission rejected = newSubmission(group, SubmissionStatus.REJECTED, now);
        Submission published = newSubmission(group, SubmissionStatus.PUBLISHED, now);
        Submission otherGroupsWaiting = newSubmission(otherGroup, SubmissionStatus.PENDING_REVIEW, now);

        List<Submission> halted = submissions.haltUnpublishedForGroup(
                group, "Halted for the test", Instant.now().truncatedTo(ChronoUnit.MILLIS));

        assertEquals(2, halted.size());
        assertTrue(halted.stream().map(Submission::getId).toList().containsAll(List.of(waitingA.getId(), rejected.getId())));
        assertEquals(SubmissionStatus.HELD, submissions.findById(waitingA.getId()).orElseThrow().getStatus());
        assertEquals("Halted for the test", submissions.findById(rejected.getId()).orElseThrow().getStatusReason());
        assertEquals(SubmissionStatus.PUBLISHED, submissions.findById(published.getId()).orElseThrow().getStatus());
        assertEquals(SubmissionStatus.PENDING_REVIEW,
                submissions.findById(otherGroupsWaiting.getId()).orElseThrow().getStatus());
    }
}
