package com.votrip.trip.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A user's membership on a trip, per docs/02-SRS-ERD.md section 5.
 *
 * <p>This is the one table every resource-level authorization check resolves through - never
 * {@code Trip.createdBy} (docs/02-SRS-ERD.md, "Key design decisions worth knowing about later").
 * See com.votrip.trip.service.TripAccessService.
 */
@Entity
@Table(name = "trip_members")
public class TripMember {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "trip_id", nullable = false, updatable = false)
    private UUID tripId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "role", nullable = false, length = 20)
    private TripMemberRole role;

    @Column(name = "status", nullable = false, length = 20)
    private TripMemberStatus status;

    @Column(name = "invited_by")
    private UUID invitedBy;

    @Column(name = "joined_at")
    private Instant joinedAt;

    protected TripMember() {
        // for JPA
    }

    private TripMember(
            UUID id,
            UUID tripId,
            UUID userId,
            TripMemberRole role,
            TripMemberStatus status,
            UUID invitedBy,
            Instant joinedAt) {
        this.id = id;
        this.tripId = tripId;
        this.userId = userId;
        this.role = role;
        this.status = status;
        this.invitedBy = invitedBy;
        this.joinedAt = joinedAt;
    }

    /**
     * An already-joined membership row - the shape both real joining paths land on (FR-3.4): the
     * creator-as-guide row {@link #creatorAsGuide} inserts today, and the self-join-via-invite-code
     * row the future join flow will insert as role=member.
     */
    public static TripMember joinedAs(UUID tripId, UUID userId, TripMemberRole role) {
        return new TripMember(UUID.randomUUID(), tripId, userId, role, TripMemberStatus.JOINED, null, Instant.now());
    }

    /** The row TripService.create inserts for the trip's creator. */
    public static TripMember creatorAsGuide(UUID tripId, UUID userId) {
        return joinedAs(tripId, userId, TripMemberRole.GUIDE);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTripId() {
        return tripId;
    }

    public UUID getUserId() {
        return userId;
    }

    public TripMemberRole getRole() {
        return role;
    }

    public TripMemberStatus getStatus() {
        return status;
    }

    public UUID getInvitedBy() {
        return invitedBy;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
