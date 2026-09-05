package com.votrip.trip.dto;

/** GET /api/v1/trips/{id}/invite-code body - guide-only, see TripResponse's invite code note. */
public record InviteCodeResponse(String inviteCode) {
}
