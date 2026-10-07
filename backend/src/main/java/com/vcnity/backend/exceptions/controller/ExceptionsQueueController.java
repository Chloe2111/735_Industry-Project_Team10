package com.vcnity.backend.exceptions.controller;

import com.vcnity.backend.exceptions.model.ExceptionItem;
import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Errors are not handled here: ApiErrorHandler answers them for every controller in the same shape,
 * { "message": "..." } with 400 for a bad request and 409 for a review that is blocked.
 */
@RestController
@RequestMapping("/api/exceptions")
public class ExceptionsQueueController {
    private final ExceptionsQueueService service;

    public ExceptionsQueueController(ExceptionsQueueService service) { this.service = service; }

    @GetMapping
    public List<ExceptionItem> list(@RequestParam(defaultValue = "false") boolean pendingOnly) {
        return pendingOnly ? service.listPending() : service.list();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExceptionItem> get(@PathVariable String id) {
        return service.get(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Open to any caller, so the service ignores the id and review fields and checks any source tag. */
    @PostMapping
    public ResponseEntity<ExceptionItem> add(@RequestBody(required = false) ExceptionItem item) {
        ExceptionItem created = service.addExternal(item);
        return ResponseEntity.created(URI.create("/api/exceptions/" + created.getId())).body(created);
    }

    @PatchMapping("/{id}/clear")
    public ResponseEntity<ExceptionItem> clear(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        return service.clear(id, noteFrom(body)).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<ExceptionItem> reject(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        return service.reject(id, noteFrom(body)).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static String noteFrom(Map<String, String> body) {
        return body == null ? "" : body.getOrDefault("note", "");
    }
}
