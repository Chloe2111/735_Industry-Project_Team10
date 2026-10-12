package com.vcnity.backend.security;

import java.util.Set;

public record CoverageResult(boolean isComplete, Set<String> missingSpeakers) {
}