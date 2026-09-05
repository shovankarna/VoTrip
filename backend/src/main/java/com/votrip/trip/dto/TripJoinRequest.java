package com.votrip.trip.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/v1/trips/join body. */
public record TripJoinRequest(@NotBlank String inviteCode) {
}
