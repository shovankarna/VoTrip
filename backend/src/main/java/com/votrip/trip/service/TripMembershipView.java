package com.votrip.trip.service;

import com.votrip.trip.entity.Trip;
import com.votrip.trip.entity.TripMemberRole;

/**
 * A trip paired with the caller's own role on it - what {@code TripService}'s read/write methods
 * return, so a controller always has what it needs to build a {@code TripResponse} (including
 * {@code myRole}) without a second query.
 */
public record TripMembershipView(Trip trip, TripMemberRole role) {
}
