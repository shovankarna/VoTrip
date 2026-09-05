package com.votrip.trip.service;

import com.votrip.common.exception.ApiException;
import com.votrip.trip.dto.TripCreateRequest;
import com.votrip.trip.dto.TripUpdateRequest;
import com.votrip.trip.entity.Trip;
import com.votrip.trip.entity.TripMember;
import com.votrip.trip.entity.TripMemberRole;
import com.votrip.trip.entity.TripMemberStatus;
import com.votrip.trip.repository.TripMemberRepository;
import com.votrip.trip.repository.TripRepository;
import com.votrip.user.entity.User;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Trip creation, reads, updates, and the invite-code join flow.
 *
 * <p>Every method that needs to know "is this caller allowed to do this" resolves it through
 * {@link TripAccessService} itself, rather than trusting a controller to have checked first - see
 * that class's javadoc for why (docs/02-SRS-ERD.md: access checks always resolve through
 * TRIP_MEMBER, never {@code Trip.createdBy}).
 */
@Service
public class TripService {

    private static final Logger log = LoggerFactory.getLogger(TripService.class);

    private static final String INVITE_CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int INVITE_CODE_LENGTH = 8;
    private static final int MAX_INVITE_CODE_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TripRepository trips;
    private final TripMemberRepository tripMembers;
    private final TripAccessService tripAccess;

    /**
     * Each create attempt runs in its own transaction, same reasoning as
     * UserProvisioningService's uid race handling: if the invite-code UNIQUE constraint rejects an
     * insert, Postgres aborts that transaction, so retrying the save has to happen in a fresh one
     * rather than reusing the poisoned one.
     */
    private final TransactionTemplate inItsOwnTransaction;

    public TripService(
            TripRepository trips,
            TripMemberRepository tripMembers,
            TripAccessService tripAccess,
            PlatformTransactionManager transactions) {
        this.trips = trips;
        this.tripMembers = tripMembers;
        this.tripAccess = tripAccess;
        this.inItsOwnTransaction = new TransactionTemplate(transactions);
        this.inItsOwnTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Creates a trip and inserts the creator's role=guide/status=joined membership row in the
     * same transaction. Retries with a freshly generated invite code on the rare chance a
     * concurrent create grabs the same code between this method's existence check and its insert.
     */
    public Trip create(User creator, TripCreateRequest request) {
        for (int attempt = 1; attempt <= MAX_INVITE_CODE_ATTEMPTS; attempt++) {
            String inviteCode = generateUniqueInviteCode();
            try {
                int currentAttempt = attempt;
                return inItsOwnTransaction.execute(status -> {
                    Trip trip =
                            trips.save(
                                    Trip.create(
                                            request.name(),
                                            request.destination(),
                                            request.coverImageUrl(),
                                            request.startDate(),
                                            request.endDate(),
                                            request.tripType(),
                                            request.templateId(),
                                            creator.getId(),
                                            inviteCode));
                    tripMembers.save(TripMember.creatorAsGuide(trip.getId(), creator.getId()));
                    log.info(
                            "Created trip {} for {} (invite code attempt {})",
                            trip.getId(),
                            creator.getId(),
                            currentAttempt);
                    return trip;
                });
            } catch (DataIntegrityViolationException e) {
                log.debug("Invite code collided on attempt {}, regenerating", attempt);
            }
        }
        throw new IllegalStateException(
                "Could not generate a unique invite code after " + MAX_INVITE_CODE_ATTEMPTS + " attempts");
    }

    @Transactional(readOnly = true)
    public List<TripMembershipView> findMemberTrips(UUID callerId) {
        List<TripMember> memberships = tripMembers.findByUserIdAndStatus(callerId, TripMemberStatus.JOINED);
        if (memberships.isEmpty()) {
            return List.of();
        }

        Map<UUID, TripMemberRole> roleByTripId =
                memberships.stream().collect(Collectors.toMap(TripMember::getTripId, TripMember::getRole));

        return trips.findAllById(roleByTripId.keySet()).stream()
                .sorted(Comparator.comparing(Trip::getCreatedAt).reversed())
                .map(trip -> new TripMembershipView(trip, roleByTripId.get(trip.getId())))
                .toList();
    }

    /**
     * 404 before 403: trip ids are opaque UUIDs, not practically guessable, so telling "doesn't
     * exist" apart from "exists but you're not on it" doesn't leak anything meaningful here -
     * unlike {@link #joinByInviteCode}, where the code itself is the thing being probed and both
     * cases must look identical.
     */
    @Transactional(readOnly = true)
    public TripMembershipView getVisibleTrip(UUID tripId, UUID callerId) {
        Trip trip = trips.findById(tripId).orElseThrow(TripService::tripNotFound);
        TripMemberRole role = tripAccess.requireMembership(tripId, callerId);
        return new TripMembershipView(trip, role);
    }

    /** Same 404-before-403 reasoning as {@link #getVisibleTrip}. */
    @Transactional
    public Trip update(UUID tripId, UUID callerId, TripUpdateRequest request) {
        Trip trip = trips.findById(tripId).orElseThrow(TripService::tripNotFound);
        tripAccess.requireGuide(tripId, callerId);
        trip.applyUpdate(
                request.name(),
                request.destination(),
                request.coverImageUrl(),
                request.startDate(),
                request.endDate(),
                request.tripType());
        return trips.save(trip);
    }

    @Transactional(readOnly = true)
    public String getInviteCode(UUID tripId, UUID callerId) {
        Trip trip = trips.findById(tripId).orElseThrow(TripService::tripNotFound);
        tripAccess.requireGuide(tripId, callerId);
        return trip.getInviteCode();
    }

    /**
     * Guide-triggered and low-frequency, unlike {@link #create} - a plain existence-check-and-
     * retry is proportionate here rather than the same REQUIRES_NEW-per-attempt machinery.
     */
    @Transactional
    public Trip regenerateInviteCode(UUID tripId, UUID callerId) {
        Trip trip = trips.findById(tripId).orElseThrow(TripService::tripNotFound);
        tripAccess.requireGuide(tripId, callerId);
        trip.regenerateInviteCode(generateUniqueInviteCode());
        return trips.save(trip);
    }

    /**
     * Self-join via the shareable code (FR-3.4). Three cases beyond a fresh join, per the schema's
     * own (trip_id, user_id) uniqueness - there is no "insert a second row," only decide what to
     * do with the one that may already exist:
     *
     * <ul>
     *   <li>status=joined - idempotent no-op, return the trip as-is
     *   <li>status=invited - accept it (someone invited who then also used the shareable link is
     *       still just joining)
     *   <li>status=removed - reject. Removal is a guide's deliberate exclusion decision; silently
     *       letting someone back in via a code they still happen to have would undercut it. They
     *       need a guide to invite them back, not a self-service reactivation.
     * </ul>
     */
    @Transactional
    public TripMembershipView joinByInviteCode(User caller, String inviteCode) {
        Trip trip =
                trips.findByInviteCode(inviteCode)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "INVITE_CODE_NOT_FOUND",
                                                "No trip found for that invite code."));

        Optional<TripMember> existing = tripMembers.findByTripIdAndUserId(trip.getId(), caller.getId());
        if (existing.isPresent()) {
            TripMember membership = existing.get();
            switch (membership.getStatus()) {
                case JOINED -> { }
                case INVITED -> tripMembers.save(markJoined(membership));
                case REMOVED ->
                        throw new ApiException(
                                HttpStatus.FORBIDDEN,
                                "MEMBERSHIP_REVOKED",
                                "You've been removed from this trip. Ask a guide to invite you back.");
            }
            return new TripMembershipView(trip, membership.getRole());
        }

        TripMember membership =
                tripMembers.save(TripMember.joinedAs(trip.getId(), caller.getId(), TripMemberRole.MEMBER));
        return new TripMembershipView(trip, membership.getRole());
    }

    private static TripMember markJoined(TripMember membership) {
        membership.markJoined();
        return membership;
    }

    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < MAX_INVITE_CODE_ATTEMPTS; attempt++) {
            String candidate = randomInviteCode();
            if (!trips.existsByInviteCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Could not find an unused invite code after " + MAX_INVITE_CODE_ATTEMPTS + " attempts");
    }

    private static String randomInviteCode() {
        StringBuilder code = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            code.append(INVITE_CODE_ALPHABET.charAt(RANDOM.nextInt(INVITE_CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    private static ApiException tripNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "No such trip.");
    }
}
