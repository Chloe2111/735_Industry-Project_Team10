package com.vcnity.backend.intake;

import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The intake endpoints: status codes and response bodies the form relies on. */
class IntakeControllerTest {

    private RecordingPipeline pipeline;
    private IntakeController controller;

    @BeforeEach
    void setUp() {
        pipeline = new RecordingPipeline();
        IntakeService intake = new IntakeService(
                new GroupTierService(new InMemoryGroupTierStore()), new InMemorySubmissionStore(), pipeline,
                new ExceptionsQueueService());
        controller = new IntakeController(intake);
    }

    private void classify(String groupId, int tier) {
        controller.classify(new TierRequest(groupId, null, tier, "Mohika", "Set for the test"));
    }

    @Test
    void submitReturns201WithTheAssignedTier() {
        classify("youth group a", 2);

        ResponseEntity<SubmissionReceipt> response =
                controller.submit(new SubmissionRequest(List.of("Youth Group A"), "Great session."));

        assertEquals(201, response.getStatusCode().value());
        assertEquals(2, response.getBody().tier());
        assertEquals(SubmissionStatus.PUBLISHED, response.getBody().status());
    }

    @Test
    void submittedReceiptCanBeFetchedAgainAndUnknownIdIs404() {
        classify("youth group a", 1);
        String id = controller.submit(new SubmissionRequest(List.of("youth group a"), "Great session.")).getBody().id();

        assertEquals(200, controller.get(id).getStatusCode().value());
        assertEquals(id, controller.get(id).getBody().id());
        assertEquals(404, controller.get("missing").getStatusCode().value());
    }

    @Test
    void classifiedGroupsAreListedWithTheirTier() {
        classify("youth group a", 2);

        List<GroupTier> groups = controller.listGroups();

        assertEquals(1, groups.size());
        assertEquals("youth group a", groups.get(0).getGroupId());
        assertEquals(2, groups.get(0).getTier());
    }

    @Test
    void unclassifiedGroupIsAnswered422WithAMessageAndTheGroupIds() {
        UnclassifiedGroupException refused = assertThrows(UnclassifiedGroupException.class,
                () -> controller.submit(new SubmissionRequest(List.of("new group"), "Great session.")));

        ResponseEntity<Map<String, Object>> response = controller.unclassified(refused);

        assertEquals(422, response.getStatusCode().value());
        assertEquals(List.of("new group"), response.getBody().get("unclassifiedGroupIds"));
        assertTrue(response.getBody().get("message").toString().contains("new group"));
    }

    @Test
    void invalidRequestIsAnswered400WithAMessage() {
        IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class,
                () -> controller.submit(new SubmissionRequest(List.of("g"), " ")));

        ResponseEntity<Map<String, Object>> response = controller.badRequest(invalid);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Please write your feedback before submitting.", response.getBody().get("message"));
    }

    @Test
    void pipelineFailureIsAnswered503AndNothingIsPublished() {
        classify("youth group a", 1);
        pipeline.failsWith(new IOException("coder unreachable"));
        PipelineUnavailableException failure = assertThrows(PipelineUnavailableException.class,
                () -> controller.submit(new SubmissionRequest(List.of("youth group a"), "Great session.")));

        ResponseEntity<Map<String, Object>> response = controller.pipelineUnavailable(failure);

        assertEquals(503, response.getStatusCode().value());
        assertTrue(controller.listPublished("youth group a").isEmpty());
    }

    @Test
    void settingATierWithoutANameOrReasonIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.classify(new TierRequest("g", null, 2, "", "reason")));
        assertThrows(IllegalArgumentException.class,
                () -> controller.classify(new TierRequest("g", null, 2, "Mohika", "")));
        assertThrows(IllegalArgumentException.class, () -> controller.classify(null));
        assertTrue(controller.listGroups().isEmpty());
    }
}
