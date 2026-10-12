package com.vcnity.backend.intake;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Story 24 intake and tier-classification endpoints.
 *
 *   POST /api/intake/submissions            submit feedback           body: { groupIds: [..], text }
 *   GET  /api/intake/submissions/{id}       check what happened to a submission
 *   GET  /api/intake/submissions?groupId=   published submissions of one group
 *   GET  /api/intake/group-tiers            every classified group
 *   PUT  /api/intake/group-tiers            set a group's tier        body: { groupId, groupName?, tier, setBy, reason }
 *
 * Errors come back as { "message": "..." }, which the frontend's apiClient already displays.
 */
@RestController
@RequestMapping("/api/intake")
public class IntakeController {

    private final IntakeService intake;

    public IntakeController(IntakeService intake) {
        this.intake = intake;
    }

    @PostMapping("/submissions")
    public ResponseEntity<SubmissionReceipt> submit(@RequestBody(required = false) SubmissionRequest request) {
        SubmissionReceipt receipt = intake.submit(request);
        return ResponseEntity.created(URI.create("/api/intake/submissions/" + receipt.id())).body(receipt);
    }

    @GetMapping("/submissions/{id}")
    public ResponseEntity<SubmissionReceipt> get(@PathVariable String id) {
        return intake.getReceipt(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/submissions")
    public List<PublishedSubmission> listPublished(@RequestParam(required = false) String groupId) {
        return intake.listPublished(groupId);
    }

    @GetMapping("/group-tiers")
    public List<GroupTier> listGroups() {
        return intake.listGroups();
    }

    @PutMapping("/group-tiers")
    public ClassificationResult classify(@RequestBody(required = false) TierRequest request) {
        return intake.classifyGroup(request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", messageOf(e)));
    }

    /** 422: the request is well-formed but the group has no tier, so intake refuses it. */
    @ExceptionHandler(UnclassifiedGroupException.class)
    public ResponseEntity<Map<String, Object>> unclassified(UnclassifiedGroupException e) {
        return ResponseEntity.status(422).body(Map.of(
                "message", messageOf(e),
                "unclassifiedGroupIds", e.getGroupIds()));
    }

    @ExceptionHandler(PipelineUnavailableException.class)
    public ResponseEntity<Map<String, Object>> pipelineUnavailable(PipelineUnavailableException e) {
        return ResponseEntity.status(503).body(Map.of("message", messageOf(e)));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> databaseUnavailable(DataAccessException e) {
        return ResponseEntity.status(503).body(Map.of(
                "message", "The database is not available right now. Nothing was saved. Please try again."));
    }

    private static String messageOf(Exception e) {
        return e.getMessage() == null || e.getMessage().isBlank() ? "The request could not be completed." : e.getMessage();
    }
}
