package com.vcnity.backend.intake;

import com.vcnity.backend.exceptions.model.ExceptionItem;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * PATCH /api/exceptions/{id}/code   body { "theme": "...", "quote": "...", "note": "..." }
 *
 * A facilitator codes an idea the AI could not code. Answers the cleared queue item (200),
 * 404 if there is no such item, 400 if the request is wrong (for example the quote is not in
 * the idea) and 409 if the item cannot be coded right now. Errors are answered by ApiErrorHandler
 * as { "message": "..." }.
 *
 * It sits next to the exceptions-queue endpoints but lives in intake, because coding needs the
 * community submission and its tier.
 */
@RestController
@RequestMapping("/api/exceptions")
public class FacilitatorCodingController {

    private final FacilitatorCodingService service;

    public FacilitatorCodingController(FacilitatorCodingService service) {
        this.service = service;
    }

    @PatchMapping("/{id}/code")
    public ResponseEntity<ExceptionItem> code(@PathVariable String id,
                                              @RequestBody(required = false) Map<String, String> body) {
        Map<String, String> fields = body == null ? Map.of() : body;
        return service.code(id, fields.get("theme"), fields.get("quote"), fields.get("note"))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
