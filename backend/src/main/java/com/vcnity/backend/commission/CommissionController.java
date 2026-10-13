package com.vcnity.backend.commission;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Matches the response shape frontend/src/services/apiClient.js already
 * expects: { success: true, data } on success, { success: false, message,
 * errors: { field: message } } on a 400.
 */
@RestController
public class CommissionController {

    private final CommissionService commissionService;

    public CommissionController(CommissionService commissionService) {
        this.commissionService = commissionService;
    }

    @PostMapping("/api/commissions")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CreateCommissionRequest request) {
        Commission created = commissionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "data", created));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(
                error -> errors.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(errorBody(errors));
    }

    @ExceptionHandler(InvalidCommissionFieldException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidField(InvalidCommissionFieldException ex) {
        return ResponseEntity.badRequest().body(errorBody(Map.of(ex.getField(), ex.getMessage())));
    }

    private static Map<String, Object> errorBody(Map<String, String> errors) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", "Please fix the highlighted fields.");
        body.put("errors", errors);
        return body;
    }
}
