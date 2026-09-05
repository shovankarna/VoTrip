package com.votrip.trip.repository;

import com.votrip.trip.entity.TripMember;
import com.votrip.trip.entity.TripMemberStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripMemberRepository extends JpaRepository<TripMember, UUID> {

    /** Backed by the (trip_id, user_id) unique index - at most one row per pair. */
    Optional<TripMember> findByTripIdAndUserId(UUID tripId, UUID userId);

    /** "Which trips is this user on" - backed by idx_trip_members_user_id. */
    List<TripMember> findByUserIdAndStatus(UUID userId, TripMemberStatus status);
}
