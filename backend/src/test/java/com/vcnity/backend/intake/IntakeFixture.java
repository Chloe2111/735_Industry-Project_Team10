package com.vcnity.backend.intake;

import com.vcnity.backend.exceptions.model.ExceptionItem;
import com.vcnity.backend.exceptions.service.ExceptionsQueueService;

import java.util.List;

/**
 * Builds the intake service the way the app does, but with in-memory stores, the real
 * exceptions queue and a pipeline stand-in, so tests can run without Spring or MongoDB.
 */
class IntakeFixture {

    final InMemoryGroupTierStore tierStore = new InMemoryGroupTierStore();
    final SubmissionStore submissionStore;
    final ExceptionsQueueService queue = new ExceptionsQueueService();
    final RecordingPipeline pipeline = new RecordingPipeline();
    final IntakeService intake;

    IntakeFixture() {
        this(new InMemorySubmissionStore());
    }

    IntakeFixture(SubmissionStore submissionStore) {
        this.submissionStore = submissionStore;
        this.intake = new IntakeService(new GroupTierService(tierStore), submissionStore, pipeline, queue);
    }

    /** Sets a group's tier the way the tier panel does: sending the version of the record it last saw. */
    ClassificationResult classify(String groupId, int tier) {
        return classify(groupId, tier, "Mohika", "Set for the test");
    }

    ClassificationResult classify(String groupId, int tier, String setBy, String reason) {
        return intake.classifyGroup(new TierRequest(groupId, null, tier, setBy, reason, currentVersion(groupId)));
    }

    Long currentVersion(String groupId) {
        return tierStore.findById(GroupTierService.normaliseGroupId(groupId)).map(GroupTier::getVersion).orElse(null);
    }

    SubmissionReceipt submit(String text, String... groupIds) {
        return intake.submit(new SubmissionRequest(List.of(groupIds), text));
    }

    Submission stored(String id) {
        return submissionStore.findById(id).orElseThrow();
    }

    List<ExceptionItem> reviewItems(String submissionId) {
        return queue.listBySource(IntakeService.SOURCE_TYPE, submissionId);
    }
}
