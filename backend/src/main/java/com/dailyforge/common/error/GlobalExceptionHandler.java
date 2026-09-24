package com.dailyforge.common.error;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every exception into the one error shape (spec §4.7).
 *
 * The catch-all deliberately discards the exception's message: an unexpected failure is
 * logged in full on the server and described in one neutral sentence to the client. A
 * leaked SQL fragment or class name is both a poor experience and an information
 * disclosure.
 *
 * Everything above the catch-all exists so that an ordinary, expected failure — a wrong
 * URL, a malformed body, a missing parameter — reaches the client as itself rather than
 * as a 500. A client that cannot tell "you asked for something that does not exist" from
 * "the server is broken" will retry the wrong things.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException exception) {
        ApiError body =
                new ApiError(
                        exception.getCode().name(),
                        exception.getMessage(),
                        exception.getField(),
                        exception.getDetails());
        return ResponseEntity.status(exception.getStatus()).body(body);
    }

    /** An unmapped path is a 404, not a server fault. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(ErrorCode.NOT_FOUND, "That endpoint does not exist."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiError.of(ErrorCode.NOT_FOUND, "That method is not allowed here."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        FieldError first = exception.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String field = first == null ? null : first.getField();
        String message = first == null ? "Check the form and try again." : first.getDefaultMessage();

        return ResponseEntity.badRequest()
                .body(ApiError.ofField(ErrorCode.VALIDATION_FAILED, message, field));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(ErrorCode.VALIDATION_FAILED, "Check the form and try again."));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(
            MissingServletRequestParameterException exception) {
        return ResponseEntity.badRequest()
                .body(
                        ApiError.ofField(
                                ErrorCode.VALIDATION_FAILED,
                                "That request is missing something it needs.",
                                exception.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest()
                .body(
                        ApiError.ofField(
                                ErrorCode.VALIDATION_FAILED,
                                "That value is not in the expected format.",
                                exception.getName()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(ErrorCode.VALIDATION_FAILED, "That request body could not be read."));
    }

    /**
     * The second line of defence behind {@link StaleWrite}: that check catches a client
     * holding a days-old copy, and this catches two writes landing in the same instant,
     * where both read the same version and only one can win. Same code and same
     * recovery for the client either way — re-read, then decide.
     */
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(
            org.springframework.orm.ObjectOptimisticLockingFailureException exception) {
        log.info("Optimistic lock conflict on {}", exception.getPersistentClassName());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        ApiError.of(
                                ErrorCode.STALE_WRITE,
                                "That changed somewhere else while you were saving. Reload to see the latest."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        ApiError.of(
                                ErrorCode.INTERNAL_ERROR,
                                "Something went wrong on our side. Your input was not lost — try again."));
    }
}
