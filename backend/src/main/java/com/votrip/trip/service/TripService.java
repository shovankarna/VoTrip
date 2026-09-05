package com.votrip.trip.service;

import com.votrip.trip.entity.Trip;
import com.votrip.trip.entity.TripMember;
import com.votrip.trip.repository.TripMemberRepository;
import com.votrip.trip.repository.TripRepository;
import com.votrip.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trip creation.
 *
 * <p>Per docs/02-SRS-ERD.md ("Key design decisions worth knowing about later"): creating a trip
 * always inserts a TRIP_MEMBER row with role=guide for the creator, in the same transaction.
 * Access checks never fall back to {@code Trip.createdBy} - see {@link TripAccessService}.
 */
@Service
public class TripService {

    private final TripRepository trips;
    private final TripMemberRepository tripMembers;

    public TripService(TripRepository trips, TripMemberRepository tripMembers) {
        this.trips = trips;
        this.tripMembers = tripMembers;
    }

    // When destination/dates/template_id are added here, take a request object instead of
    // growing this into more positional parameters.
    @Transactional
    public Trip create(User creator, String name) {
        Trip trip = trips.save(Trip.create(name, creator.getId()));
        tripMembers.save(TripMember.creatorAsGuide(trip.getId(), creator.getId()));
        return trip;
    }
}
