package com.dailyforge.common.error;

import java.util.Map;

/**
 * The one error shape the API ever returns (spec §4.7):
 *
 * <pre>
 * { "code": "HABIT_LOCKED", "message": "…", "field": null, "details": {} }
 * </pre>
 *
 * A stack trace or a raw Java message never reaches a client.
 */
public record ApiError(String code, String message, String field, Map<String, Object> details) {

    public static ApiError of(ErrorCode code, String message) {
        return new ApiError(code.name(), message, null, Map.of());
    }

    public static ApiError ofField(ErrorCode code, String message, String field) {
        return new ApiError(code.name(), message, field, Map.of());
    }

    public static ApiError withDetails(ErrorCode code, String message, Map<String, Object> details) {
        return new ApiError(code.name(), message, null, details);
    }
}
