package com.votrip.trip.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/**
 * A trip, per docs/02-SRS-ERD.md section 5.
 *
 * <p>{@code status} is a real Java enum ({@link TripStatus}): lifecycle transitions (FR-3.2)
 * branch on it and must reject illegal jumps, the same category as
 * {@link TripMember#getRole()}/{@link TripMember#getStatus()}. {@code tripType} stays a plain,
 * app-validated string backed only by the V2 migration's CHECK constraint - docs/04-FutureScope.md
 * section 4 flags native enum-style rigidity as expensive to change once data exists and calls
 * {@code EXPENSE.category}'s free-string approach the right precedent, and {@code trip_type} in
 * particular is expected to grow when community matching (docs/04-FutureScope.md section 2) adds
 * categories. Both columns stay VARCHAR + CHECK in the database either way; the difference is
 * only in how the Java side represents them.
 */
@Entity
@Table(name = "trips")
public class Trip {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "cover_image_url", length = 2048)
    private String coverImageUrl;

    @Column(name = "destination", length = 255)
    private String destination;

    // Reserved, no FK - ITINERARY_TEMPLATE doesn't exist yet. See V2 migration.
    @Column(name = "template_id")
    private UUID templateId;

    // Reserved, no FK - ORGANIZATION doesn't exist yet. See V2 migration.
    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "status", nullable = false, length = 20)
    private TripStatus status;

    @Column(name = "trip_type", nullable = false, length = 20)
    private String tripType;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "invite_code", nullable = false, unique = true, length = 32)
    private String inviteCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Trip() {
        // for JPA
    }

    private Trip(
            UUID id,
            String name,
            UUID createdBy,
            TripStatus status,
            String tripType,
            String currency,
            String inviteCode,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.createdBy = createdBy;
        this.status = status;
        this.tripType = tripType;
        this.currency = currency;
        this.inviteCode = inviteCode;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * A brand-new, blank trip created by {@code createdBy}. Sets the same defaults the V2
     * migration's columns default to (status=planning, tripType=custom, currency=INR) explicitly
     * in Java too, rather than relying on them silently - same reasoning as {@code User.active}
     * in the V1 migration/entity.
     */
    public static Trip create(String name, UUID createdBy) {
        Instant now = Instant.now();
        return new Trip(
                UUID.randomUUID(),
                name,
                createdBy,
                TripStatus.PLANNING,
                "custom",
                "INR",
                generateInviteCode(),
                now,
                now);
    }

    /**
     * 40 bits of randomness - astronomically low collision odds, not worth a retry loop for the
     * schema/authorization phase this exists for. Revisit if/when a real create endpoint needs to
     * prove itself under FR-4.4-style concurrency.
     */
    private static String generateInviteCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public String getDestination() {
        return destination;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public TripStatus getStatus() {
        return status;
    }

    public String getTripType() {
        return tripType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getCurrency() {
        return currency;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
