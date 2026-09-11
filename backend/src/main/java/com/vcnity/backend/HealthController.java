package com.vcnity.backend;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proves the app boots and reads its Mongo config; not a feature endpoint.
 * Response shape matches what the frontend's apiClient already expects
 * ({ success, data }), so real endpoints built later can follow the same
 * convention without the frontend changing.
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of("success", true, "data", Map.of("status", "ok"));
    }
}
