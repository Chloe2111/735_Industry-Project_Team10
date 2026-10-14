package com.vcnity.backend.findings;

import com.vcnity.backend.security.CodedFinding;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Retrieves stored pipeline findings.
 *
 * These responses do not establish human approval.
 * Access control must be integrated before use with real client data.
 */
@RestController
@RequestMapping("/api/findings")
@ConditionalOnProperty(
        name = "findings.api.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class FindingController {

    private final FindingRepository repository;

    public FindingController(FindingRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{itemId}")
    public ResponseEntity<CodedFinding> findByItemId(
            @PathVariable("itemId") String itemId
    ) {
        return repository.findByItemId(itemId)
                .map(finding -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .body(finding))
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .cacheControl(CacheControl.noStore())
                        .build());
    }

    @GetMapping
    public ResponseEntity<FindingRepository.FindingPage> findBySource(
            @RequestParam("sourceRef") String sourceRef,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        FindingRepository.FindingPage result =
                repository.findBySourceRef(sourceRef, page, size);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(result);
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiError> invalidRequest(
            Exception exception
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_FINDING_REQUEST"
        );
    }

    @ExceptionHandler({
            DataAccessResourceFailureException.class,
            TransientDataAccessException.class
    })
    public ResponseEntity<ApiError> storageUnavailable(
            DataAccessException exception
    ) {
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "FINDING_STORAGE_UNAVAILABLE"
        );
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> storageError(
            DataAccessException exception
    ) {
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "FINDING_STORAGE_ERROR"
        );
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            String code
    ) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(new ApiError(code, messageFor(code)));
    }

    private static String messageFor(String code) {
        return switch (code) {
            case "INVALID_FINDING_REQUEST" ->
                    "Invalid findings request. Provide a sourceRef, a page of 0 or greater, and a size between 1 and 100.";
            case "FINDING_STORAGE_UNAVAILABLE" ->
                    "Findings are temporarily unavailable. Please try again later.";
            case "FINDING_STORAGE_ERROR" ->
                    "Could not load coded findings. Please try again later.";
            default ->
                    "Could not complete the findings request. Please try again later.";
        };
    }

    public record ApiError(String code, String message) {
    }
}