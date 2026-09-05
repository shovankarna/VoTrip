package com.votrip.trip.entity;

/**
 * {@code TRIP_MEMBER.status}, per docs/02-SRS-ERD.md section 5 ("Trip joining").
 *
 * <p>A real Java enum for the same reason as {@link TripMemberRole}: membership checks branch on
 * whether a row is {@link #JOINED}, not just what it says in the database.
 */
public enum TripMemberStatus {
    INVITED("invited"),
    JOINED("joined"),
    REMOVED("removed");

    private final String value;

    TripMemberStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static TripMemberStatus fromValue(String value) {
        for (TripMemberStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown trip member status: " + value);
    }
}
