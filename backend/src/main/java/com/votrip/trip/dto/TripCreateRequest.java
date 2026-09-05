package com.votrip.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * POST /api/v1/trips body. Only {@code name} is required - every other field is optional at
 * creation (FR-3.1 lists them as trip attributes, not as required-at-creation).
 *
 * <p>{@code tripType} is validated against the allowed set in {@code Trip.create}, not here -
 * that keeps the one place enforcing "what values are legal" in sync with the entity rather than
 * duplicated across every DTO that carries it (this one and {@link TripUpdateRequest}).
 * {@code templateId} is accepted but unused until ITINERARY_TEMPLATE exists.
 */
public record TripCreateRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String destination,
        @Size(max = 2048) String coverImageUrl,
        LocalDate startDate,
        LocalDate endDate,
        String tripType,
        UUID templateId) {
}
