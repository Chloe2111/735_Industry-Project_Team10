package com.vcnity.backend.common;

import com.vcnity.backend.exceptions.service.ReviewBlockedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Every controller answers errors through this one handler, always as { "message": ... }. */
class ApiErrorHandlerTest {

    private final ApiErrorHandler handler = new ApiErrorHandler();

    @Test
    void invalidRequestIs400WithItsMessage() {
        ResponseEntity<Map<String, Object>> response =
                handler.badRequest(new IllegalArgumentException("A reviewer note is required to reject an exception"));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("A reviewer note is required to reject an exception", response.getBody().get("message"));
    }

    @Test
    void blockedReviewIs409NotASuccess() {
        ResponseEntity<Map<String, Object>> response =
                handler.apiError(new ReviewBlockedException("Halted: the group is now Tier 3."));

        assertEquals(409, response.getStatusCode().value());
        assertEquals("Halted: the group is now Tier 3.", response.getBody().get("message"));
    }

    @Test
    void apiExceptionCarriesItsOwnStatusAndExtraDetails() {
        ApiException withDetails = new ApiException(422, "No tier has been set for: new group") {
            @Override
            public Map<String, Object> details() {
                return Map.of("unclassifiedGroupIds", List.of("new group"));
            }
        };

        ResponseEntity<Map<String, Object>> response = handler.apiError(withDetails);

        assertEquals(422, response.getStatusCode().value());
        assertEquals("No tier has been set for: new group", response.getBody().get("message"));
        assertEquals(List.of("new group"), response.getBody().get("unclassifiedGroupIds"));
    }

    @Test
    void missingMessageStillGivesTheCallerSomethingToShow() {
        ResponseEntity<Map<String, Object>> response = handler.badRequest(new IllegalArgumentException());

        assertEquals(400, response.getStatusCode().value());
        assertFalse(response.getBody().get("message").toString().isBlank());
    }
}
