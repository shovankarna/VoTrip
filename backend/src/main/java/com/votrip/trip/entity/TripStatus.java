package com.votrip.trip.entity;

/**
 * {@code TRIP.status}, per docs/02-SRS-ERD.md FR-3.2 (planning -> fixed -> ongoing -> ended,
 * with cancelled reachable from any pre-ended state).
 *
 * <p>A real Java enum, unlike {@link Trip#getTripType()}: lifecycle transitions branch on this
 * value and must reject illegal jumps, so it gets compile-time safety - not just the V2
 * migration's DB-level CHECK. {@code tripType} stays a plain string because FR-3.3 calls it
 * informational only, with no behavior difference between values.
 */
public enum TripStatus {
    PLANNING("planning"),
    FIXED("fixed"),
    ONGOING("ongoing"),
    ENDED("ended"),
    CANCELLED("cancelled");

    private final String value;

    TripStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static TripStatus fromValue(String value) {
        for (TripStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown trip status: " + value);
    }
}
