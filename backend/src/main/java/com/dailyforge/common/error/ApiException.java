package com.dailyforge.common.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * The exception every module throws for an expected failure. Carrying the code and the
 * status together means the handler does not have to guess either.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final HttpStatus status;
    private final String field;
    private final Map<String, Object> details;

    public ApiException(ErrorCode code, HttpStatus status, String message) {
        this(code, status, message, null, Map.of());
    }

    public ApiException(
            ErrorCode code,
            HttpStatus status,
            String message,
            String field,
            Map<String, Object> details) {
        super(message);
        this.code = code;
        this.status = status;
        this.field = field;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static ApiException authRequired() {
        return new ApiException(ErrorCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED, "Sign in to do that.");
    }

    public static ApiException notFound(String what) {
        return new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, what + " was not found.");
    }

    public static ApiException forbidden() {
        return new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "That is not yours.");
    }

    /** Guardrail copy is neutral by design: it points at the value, not at the person. */
    public static ApiException outOfRange(String field, String message) {
        return new ApiException(
                ErrorCode.OUT_OF_RANGE, HttpStatus.UNPROCESSABLE_CONTENT, message, field, Map.of());
    }

    public ErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getField() {
        return field;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
