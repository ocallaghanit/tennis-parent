package com.tennis.adapter.dto;

import java.time.Instant;

/**
 * Standardized error response format.
 */
public record ApiError(
        String code,
        String message,
        String path,
        Instant timestamp
) {
    public ApiError(String code, String message, String path) {
        this(code, message, path, Instant.now());
    }

    public ApiError(String code, String message) {
        this(code, message, null, Instant.now());
    }
}

