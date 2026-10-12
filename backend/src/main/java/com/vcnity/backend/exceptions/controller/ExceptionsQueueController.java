package com.vcnity.backend.exceptions.controller;

import com.vcnity.backend.exceptions.model.ExceptionItem;
import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

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

    @PostMapping
    public ResponseEntity<?> add(@RequestBody ExceptionItem item) {
        try {
            ExceptionItem created = service.add(item);
            return ResponseEntity.created(URI.create("/api/exceptions/" + created.getId())).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/clear")
    public ResponseEntity<?> clear(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        return review(id, body, true);
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        return review(id, body, false);
    }

    private ResponseEntity<?> review(String id, Map<String, String> body, boolean clear) {
        String note = body == null ? "" : body.getOrDefault("note", "");
        try {
            var result = clear ? service.clear(id, note) : service.reject(id, note);
            return result.<ResponseEntity<?>>map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
