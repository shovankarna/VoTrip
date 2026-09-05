package com.votrip.trip.repository;

import com.votrip.trip.entity.Trip;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    /** The join-by-code lookup. Backed by the unique index on invite_code. */
    Optional<Trip> findByInviteCode(String inviteCode);

    /** Fast-path collision check before generating a candidate invite code. */
    boolean existsByInviteCode(String inviteCode);
}
