package com.bizpilot.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Standard error response shape returned by the API for all error conditions.
 * See docs/security.md for the contract this implements.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors
) {

    public static ApiError of(int status, String code, String message, String path) {
        return new ApiError(Instant.now(), status, code, message, path, null);
    }

    public static ApiError ofValidation(int status, String code, String message, String path,
                                         Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, code, message, path, fieldErrors);
    }
}
