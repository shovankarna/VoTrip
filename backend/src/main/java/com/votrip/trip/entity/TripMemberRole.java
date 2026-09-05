package com.votrip.trip.entity;

/**
 * {@code TRIP_MEMBER.role}, per docs/02-SRS-ERD.md section 5.
 *
 * <p>A real Java enum, unlike {@link Trip#getStatus()}/{@link Trip#getTripType()} - every
 * resource-authorization check in the app branches on this value (see
 * com.votrip.trip.service.TripAccessService), so it gets compile-time safety, not just the V2
 * migration's DB-level CHECK.
 */
public enum TripMemberRole {
    GUIDE("guide"),
    MEMBER("member");

    private final String value;

    TripMemberRole(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static TripMemberRole fromValue(String value) {
        for (TripMemberRole role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown trip member role: " + value);
    }
}
