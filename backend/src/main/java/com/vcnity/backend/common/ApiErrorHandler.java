package com.vcnity.backend.common;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The one place where the exceptions-queue and intake controllers turn errors into responses.
 * Every error body is { "message": "..." } (plus any details), which the frontend's apiClient
 * already knows how to show.
 *
 *   IllegalArgumentException -> 400  the request itself is wrong
 *   ApiException             -> the status the exception carries (409, 422, 503, ...)
 *   DataAccessException      -> 503  the database could not be reached; nothing was saved
 */
@RestControllerAdvice(basePackages = {"com.vcnity.backend.exceptions", "com.vcnity.backend.intake"})
public class ApiErrorHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(400).body(body(e.getMessage(), Map.of()));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> apiError(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(body(e.getMessage(), e.details()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> databaseUnavailable(DataAccessException e) {
        return ResponseEntity.status(503).body(body(
                "The database is not available right now. Nothing was saved. Please try again.", Map.of()));
    }

    private static Map<String, Object> body(String message, Map<String, Object> details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message == null || message.isBlank() ? "The request could not be completed." : message);
        body.putAll(details);
        return body;
    }
}
