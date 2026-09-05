package com.votrip.trip.dto;

import com.votrip.trip.entity.Trip;
import com.votrip.trip.entity.TripMemberRole;
import com.votrip.trip.entity.TripStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The trip shape every trip endpoint returns. Mirrors the TRIP entity from
 * docs/02-SRS-ERD.md section 5, plus {@code myRole} - the caller's own role on this trip,
 * computed server-side from the same TripAccessService lookup the authorization check already
 * made, so clients don't need a second round trip to decide what controls to show.
 *
 * <p>{@code inviteCode} is guide-only: it's a join credential, and regeneration already being
 * guide-only implies the guide controls who gets in - exposing it to every member would undercut
 * that. Non-guide callers get {@code null} here; see {@code GET /api/v1/trips/{id}/invite-code}
 * for the dedicated guide-only lookup.
 */
public record TripResponse(
        UUID id,
        String name,
        String destination,
        String coverImageUrl,
        UUID templateId,
        UUID organizationId,
        UUID createdBy,
        TripStatus status,
        String tripType,
        LocalDate startDate,
        LocalDate endDate,
        String currency,
        String inviteCode,
        TripMemberRole myRole,
        Instant createdAt,
        Instant updatedAt) {

    public static TripResponse from(Trip trip, TripMemberRole myRole) {
        return new TripResponse(
                trip.getId(),
                trip.getName(),
                trip.getDestination(),
                trip.getCoverImageUrl(),
                trip.getTemplateId(),
                trip.getOrganizationId(),
                trip.getCreatedBy(),
                trip.getStatus(),
                trip.getTripType(),
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getCurrency(),
                myRole == TripMemberRole.GUIDE ? trip.getInviteCode() : null,
                myRole,
                trip.getCreatedAt(),
                trip.getUpdatedAt());
    }
}
