package com.vcnity.backend.intake;

import com.vcnity.backend.common.ApiErrorHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The intake endpoints: status codes and response bodies the form relies on.
 * Errors are answered by the shared ApiErrorHandler, exactly as they are for the exceptions queue.
 */
class IntakeControllerTest {

    private IntakeFixture app;
    private IntakeController controller;
    private final ApiErrorHandler errors = new ApiErrorHandler();

    @BeforeEach
    void setUp() {
        app = new IntakeFixture();
        controller = new IntakeController(app.intake);
    }

    @Test
    void submitReturns201WithTheAssignedTier() {
        app.classify("youth group a", 2);

        ResponseEntity<SubmissionReceipt> response =
                controller.submit(new SubmissionRequest(List.of("Youth Group A"), "Great session."));

        assertEquals(201, response.getStatusCode().value());
        assertEquals(2, response.getBody().tier());
        assertEquals(SubmissionStatus.PUBLISHED, response.getBody().status());
    }

    @Test
    void submittedReceiptCanBeFetchedAgainAndUnknownIdIs404() {
        app.classify("youth group a", 1);
        String id = controller.submit(new SubmissionRequest(List.of("youth group a"), "Great session.")).getBody().id();

        assertEquals(200, controller.get(id).getStatusCode().value());
        assertEquals(id, controller.get(id).getBody().id());
        assertEquals(404, controller.get("missing").getStatusCode().value());
    }

    @Test
    void classifiedGroupsAreListedWithTheirTierAndVersion() {
        controller.classify(new TierRequest("Youth Group A", null, 2, "Mohika", "Personal stories", null));

        List<GroupTier> groups = controller.listGroups();

        assertEquals(1, groups.size());
        assertEquals("youth group a", groups.get(0).getGroupId());
        assertEquals(2, groups.get(0).getTier());
        assertNotNull(groups.get(0).getVersion(), "the tier panel needs the version to send back with a change");
    }

    @Test
    void unclassifiedGroupIsAnswered422WithAMessageAndTheGroupIds() {
        UnclassifiedGroupException refused = assertThrows(UnclassifiedGroupException.class,
                () -> controller.submit(new SubmissionRequest(List.of("new group"), "Great session.")));

        ResponseEntity<Map<String, Object>> response = errors.apiError(refused);

        assertEquals(422, response.getStatusCode().value());
        assertEquals(List.of("new group"), response.getBody().get("unclassifiedGroupIds"));
        assertTrue(response.getBody().get("message").toString().contains("new group"));
    }

    @Test
    void invalidRequestIsAnswered400WithAMessage() {
        IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class,
                () -> controller.submit(new SubmissionRequest(List.of("g"), " ")));

        ResponseEntity<Map<String, Object>> response = errors.badRequest(invalid);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Please write your feedback before submitting.", response.getBody().get("message"));
    }

    @Test
    void pipelineFailureIsAnswered503AndNothingIsPublished() {
        app.classify("youth group a", 1);
        app.pipeline.failsWith(new IOException("coder unreachable"));
        PipelineUnavailableException failure = assertThrows(PipelineUnavailableException.class,
                () -> controller.submit(new SubmissionRequest(List.of("youth group a"), "Great session.")));

        ResponseEntity<Map<String, Object>> response = errors.apiError(failure);

        assertEquals(503, response.getStatusCode().value());
        assertTrue(controller.listPublished("youth group a").isEmpty());
    }

    @Test
    void staleTierChangeIsAnswered409AndSaysWhoChangedIt() {
        controller.classify(new TierRequest("youth group a", null, 1, "Mohika", "General", null));
        Long seenByBoth = controller.listGroups().get(0).getVersion();
        controller.classify(new TierRequest("youth group a", null, 3, "Chloee", "Restricted", seenByBoth));

        ConcurrentTierChangeException stale = assertThrows(ConcurrentTierChangeException.class,
                () -> controller.classify(new TierRequest("youth group a", null, 2, "Mohika", "Sensitive", seenByBoth)));
        ResponseEntity<Map<String, Object>> response = errors.apiError(stale);

        assertEquals(409, response.getStatusCode().value());
        assertTrue(response.getBody().get("message").toString().contains("Chloee"));
        assertEquals(3, controller.listGroups().get(0).getTier());
    }

    @Test
    void settingATierWithoutANameOrReasonIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.classify(new TierRequest("g", null, 2, "", "reason", null)));
        assertThrows(IllegalArgumentException.class,
                () -> controller.classify(new TierRequest("g", null, 2, "Mohika", "", null)));
        assertThrows(IllegalArgumentException.class, () -> controller.classify(null));
        assertTrue(controller.listGroups().isEmpty());
    }
}
