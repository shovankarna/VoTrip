package com.votrip.trip.service;

import com.votrip.trip.entity.TripMember;
import com.votrip.trip.entity.TripMemberRole;
import com.votrip.trip.entity.TripMemberStatus;
import com.votrip.trip.repository.TripMemberRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * The one place every trip/itinerary/expense endpoint asks "is this user a member of this trip,
 * and with what role" - per docs/02-SRS-ERD.md, access checks always resolve through TRIP_MEMBER,
 * never {@code Trip.createdBy}. Callers should depend on this rather than querying
 * {@link TripMemberRepository} directly, so the membership rule (status must be joined) lives in
 * exactly one place.
 *
 * <p>{@link #requireMembership} / {@link #requireGuide} throw Spring Security's
 * {@link AccessDeniedException} rather than a domain-specific one: thrown from anywhere in a
 * request's call stack, it is caught by the same {@code ExceptionTranslationFilter} /
 * {@code RestAccessDeniedHandler} pair {@code SecurityConfig} already wires up for filter-level
 * denials - so a controller-triggered 403 and a service-triggered 403 reach the client identical.
 */
@Service
public class TripAccessService {

    private final TripMemberRepository tripMembers;

    public TripAccessService(TripMemberRepository tripMembers) {
        this.tripMembers = tripMembers;
    }

    /** The caller's role on this trip, or empty if they are not a joined member. */
    public Optional<TripMemberRole> roleOf(UUID tripId, UUID userId) {
        return tripMembers
                .findByTripIdAndUserId(tripId, userId)
                .filter(member -> member.getStatus() == TripMemberStatus.JOINED)
                .map(TripMember::getRole);
    }

    public boolean isMember(UUID tripId, UUID userId) {
        return roleOf(tripId, userId).isPresent();
    }

    public boolean isGuide(UUID tripId, UUID userId) {
        return roleOf(tripId, userId).filter(TripMemberRole.GUIDE::equals).isPresent();
    }

    /** @throws AccessDeniedException if the caller is not a joined member of this trip. */
    public TripMemberRole requireMembership(UUID tripId, UUID userId) {
        return roleOf(tripId, userId)
                .orElseThrow(() -> new AccessDeniedException("Not a member of this trip."));
    }

    /** @throws AccessDeniedException if the caller is not a joined guide of this trip. */
    public void requireGuide(UUID tripId, UUID userId) {
        if (!isGuide(tripId, userId)) {
            throw new AccessDeniedException("Requires the guide role on this trip.");
        }
    }
}
