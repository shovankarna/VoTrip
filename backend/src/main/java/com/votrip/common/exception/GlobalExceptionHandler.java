package com.votrip.common.exception;

import com.votrip.common.dto.ErrorResponse;
import com.votrip.config.FirebaseAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Single place that turns an exception into an {@link ErrorResponse}.
 *
 * <p>Security failures reach this class too: the entry point and access-denied handler delegate to
 * the HandlerExceptionResolver rather than writing their own JSON, so a 401 from the Firebase
 * filter and a 500 from a service look structurally identical to a client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(
            ApiException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {
        Object reason = request.getAttribute(FirebaseAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        String message =
                reason != null
                        ? "Firebase ID token was rejected: " + reason
                        : "Missing or invalid Firebase ID token.";
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", message, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        return build(
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "You do not have permission to perform this action.",
                request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(error -> error.getField() + ": " + error.getDefaultMessage())
                        .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "No such endpoint.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Log the detail server-side; never leak internals to the client.
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Something went wrong. Please try again.",
                request);
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), code, message, request.getRequestURI()));
    }
}
