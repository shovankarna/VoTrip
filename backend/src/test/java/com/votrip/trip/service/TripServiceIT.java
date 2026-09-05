package com.votrip.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.votrip.common.exception.ApiException;
import com.votrip.trip.dto.TripCreateRequest;
import com.votrip.trip.dto.TripUpdateRequest;
import com.votrip.trip.entity.Trip;
import com.votrip.trip.entity.TripMember;
import com.votrip.trip.entity.TripMemberRole;
import com.votrip.trip.entity.TripMemberStatus;
import com.votrip.trip.repository.TripMemberRepository;
import com.votrip.trip.repository.TripRepository;
import com.votrip.user.entity.User;
import com.votrip.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Trip create/read/update and the invite-code join flow, against a real Postgres running the
 * real V2 migration - same reasoning as TripAccessServiceIT/UserProvisioningServiceIT.
 *
 * <p>Some scenarios (a removed member, a pending invite) can't be reached through any endpoint
 * yet - member removal and explicit invites are a later chunk - so those rows are inserted
 * directly via JdbcTemplate, same technique UserProvisioningServiceIT already uses to prove a
 * migration-level default independently of entity code.
 */
@SpringBootTest
@Testcontainers
class TripServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @MockitoBean
    private FirebaseApp firebaseApp;

    @MockitoBean
    private FirebaseAuth firebaseAuth;

    @Autowired
    private TripService tripService;

    @Autowired
    private TripRepository trips;

    @Autowired
    private TripMemberRepository tripMembers;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    private User creator;
    private User otherUser;

    @BeforeEach
    void setUp() {
        tripMembers.deleteAll();
        trips.deleteAll();
        users.deleteAll();

        creator = users.save(User.provisionFromToken("uid-creator", "creator@example.com", "Creator", null));
        otherUser = users.save(User.provisionFromToken("uid-other", "other@example.com", "Other", null));
    }

    private static TripCreateRequest createRequest(String name) {
        return new TripCreateRequest(name, null, null, null, null, null, null);
    }

    private Trip newTrip() {
        return tripService.create(creator, createRequest("Test Trip"));
    }

    @Test
    void nonMemberCannotReadTrip() {
        Trip trip = newTrip();

        assertThatThrownBy(() -> tripService.getVisibleTrip(trip.getId(), otherUser.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getVisibleTripThrows404ForUnknownTrip() {
        assertThatThrownBy(() -> tripService.getVisibleTrip(UUID.randomUUID(), creator.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void memberCanReadButCannotPatch() {
        Trip trip = newTrip();
        tripMembers.save(TripMember.joinedAs(trip.getId(), otherUser.getId(), TripMemberRole.MEMBER));

        TripMembershipView view = tripService.getVisibleTrip(trip.getId(), otherUser.getId());
        assertThat(view.role()).isEqualTo(TripMemberRole.MEMBER);

        TripUpdateRequest patch = new TripUpdateRequest("New Name", null, null, null, null, null);
        assertThatThrownBy(() -> tripService.update(trip.getId(), otherUser.getId(), patch))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void guideCanPatch() {
        Trip trip = newTrip();

        TripUpdateRequest patch = new TripUpdateRequest("New Name", "New Destination", null, null, null, null);
        Trip updated = tripService.update(trip.getId(), creator.getId(), patch);

        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getDestination()).isEqualTo("New Destination");
    }

    @Test
    void joinByCodeCreatesMembership() {
        Trip trip = newTrip();

        TripMembershipView view = tripService.joinByInviteCode(otherUser, trip.getInviteCode());

        assertThat(view.role()).isEqualTo(TripMemberRole.MEMBER);
        assertThat(tripMembers.findByTripIdAndUserId(trip.getId(), otherUser.getId()))
                .isPresent()
                .get()
                .satisfies(m -> assertThat(m.getStatus()).isEqualTo(TripMemberStatus.JOINED));
    }

    @Test
    void joiningTwiceDoesNotDuplicate() {
        Trip trip = newTrip();

        tripService.joinByInviteCode(otherUser, trip.getInviteCode());
        long countAfterFirstJoin = tripMembers.count();

        tripService.joinByInviteCode(otherUser, trip.getInviteCode());

        assertThat(tripMembers.count()).isEqualTo(countAfterFirstJoin);
    }

    @Test
    void joinWithUnknownCodeReturns404WithoutLeakingTripExistence() {
        newTrip();

        assertThatThrownBy(() -> tripService.joinByInviteCode(otherUser, "NOPENOPE"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiException = (ApiException) e;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiException.getCode()).isEqualTo("INVITE_CODE_NOT_FOUND");
                });
    }

    @Test
    void removedMemberCannotRejoinViaCode() {
        Trip trip = newTrip();
        jdbc.update(
                "INSERT INTO trip_members (id, trip_id, user_id, role, status) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), trip.getId(), otherUser.getId(), "member", "removed");

        assertThatThrownBy(() -> tripService.joinByInviteCode(otherUser, trip.getInviteCode()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("MEMBERSHIP_REVOKED"));
    }

    @Test
    void pendingInviteAutoAcceptsOnCodeJoin() {
        Trip trip = newTrip();
        jdbc.update(
                "INSERT INTO trip_members (id, trip_id, user_id, role, status, invited_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), trip.getId(), otherUser.getId(), "member", "invited", creator.getId());

        TripMembershipView view = tripService.joinByInviteCode(otherUser, trip.getInviteCode());

        assertThat(view.role()).isEqualTo(TripMemberRole.MEMBER);
        assertThat(tripMembers.findByTripIdAndUserId(trip.getId(), otherUser.getId()))
                .isPresent()
                .get()
                .satisfies(m -> {
                    assertThat(m.getStatus()).isEqualTo(TripMemberStatus.JOINED);
                    assertThat(m.getJoinedAt()).isNotNull();
                });
    }

    @Test
    void inviteCodeIsGuideOnly() {
        Trip trip = newTrip();
        tripMembers.save(TripMember.joinedAs(trip.getId(), otherUser.getId(), TripMemberRole.MEMBER));

        assertThat(tripService.getInviteCode(trip.getId(), creator.getId())).isEqualTo(trip.getInviteCode());
        assertThatThrownBy(() -> tripService.getInviteCode(trip.getId(), otherUser.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void regeneratingInviteCodeChangesItAndRequiresGuide() {
        Trip trip = newTrip();
        String originalCode = trip.getInviteCode();
        tripMembers.save(TripMember.joinedAs(trip.getId(), otherUser.getId(), TripMemberRole.MEMBER));

        assertThatThrownBy(() -> tripService.regenerateInviteCode(trip.getId(), otherUser.getId()))
                .isInstanceOf(AccessDeniedException.class);

        Trip regenerated = tripService.regenerateInviteCode(trip.getId(), creator.getId());
        assertThat(regenerated.getInviteCode()).isNotEqualTo(originalCode);
    }

    @Test
    void listReturnsOnlyTripsCallerHasJoined() {
        Trip trip = newTrip();

        assertThat(tripService.findMemberTrips(creator.getId())).extracting(v -> v.trip().getId())
                .containsExactly(trip.getId());
        assertThat(tripService.findMemberTrips(otherUser.getId())).isEmpty();
    }
}
