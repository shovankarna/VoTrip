package com.votrip.trip.repository;

import com.votrip.trip.entity.TripMember;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripMemberRepository extends JpaRepository<TripMember, UUID> {

    /** Backed by the (trip_id, user_id) unique index - at most one row per pair. */
    Optional<TripMember> findByTripIdAndUserId(UUID tripId, UUID userId);
}
