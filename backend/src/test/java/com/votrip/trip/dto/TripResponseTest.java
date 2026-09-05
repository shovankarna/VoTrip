package com.votrip.trip.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.votrip.trip.entity.Trip;
import com.votrip.trip.entity.TripMemberRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** No Spring context needed - this is a pure mapping rule, not wiring. */
class TripResponseTest {

    @Test
    void inviteCodeIsIncludedForAGuide() {
        Trip trip = Trip.create("Test Trip", null, null, null, null, null, null, UUID.randomUUID(), "ABCD1234");

        TripResponse response = TripResponse.from(trip, TripMemberRole.GUIDE);

        assertThat(response.inviteCode()).isEqualTo("ABCD1234");
    }

    @Test
    void inviteCodeIsNullForAPlainMember() {
        Trip trip = Trip.create("Test Trip", null, null, null, null, null, null, UUID.randomUUID(), "ABCD1234");

        TripResponse response = TripResponse.from(trip, TripMemberRole.MEMBER);

        assertThat(response.inviteCode()).isNull();
    }
}
