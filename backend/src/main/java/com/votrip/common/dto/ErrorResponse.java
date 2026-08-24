package com.votrip.common.dto;

import java.time.Instant;
import java.util.List;

/**
 * The one error shape this API ever returns, so web and mobile can parse failures identically
 * (CLAUDE.md, Code Standards).
 *
 * <p>Every field is always present - {@code details} is an empty list rather than absent when there
 * is nothing field-specific to report. That keeps the matching TypeScript type in
 * {@code frontend/packages/shared-types} free of optional fields the clients would have to narrow.
 *
 * @param timestamp when the failure was produced, UTC
 * @param status    HTTP status code, mirrored here so the body is self-contained
 * @param code      stable machine-readable code for clients to branch on
 * @param message   human-readable explanation, safe to surface to a user
 * @param path      the request path that failed
 * @param details   per-field problems, empty unless this is a validation failure
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldError> details) {

    public static ErrorResponse of(int status, String code, String message, String path) {
        return new ErrorResponse(Instant.now(), status, code, message, path, List.of());
    }

    /** A single field-level validation problem. */
    public record FieldError(String field, String message) {
    }
}
