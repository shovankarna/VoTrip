package com.votrip.trip.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * PATCH /api/v1/trips/{id} body. Every field is optional; null means "leave unchanged" - see
 * {@code Trip.applyUpdate}.
 *
 * <p>This means a nullable field already set (coverImageUrl, startDate, endDate) cannot be
 * cleared back to null through this endpoint - there is no way to distinguish "not provided"
 * from "explicitly cleared" with plain nulls. Deliberately deferred: a future dedicated clear
 * endpoint, or a sentinel value, is the fix if/when a real need shows up - not built speculatively
 * here.
 */
public record TripUpdateRequest(
        @Size(max = 255) String name,
        @Size(max = 255) String destination,
        @Size(max = 2048) String coverImageUrl,
        LocalDate startDate,
        LocalDate endDate,
        String tripType) {
}
