package com.votrip.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.votrip.trip.dto.TripCreateRequest;
import com.votrip.trip.entity.Trip;
import com.votrip.trip.entity.TripMember;
import com.votrip.trip.entity.TripMemberRole;
import com.votrip.trip.repository.TripMemberRepository;
import com.votrip.trip.repository.TripRepository;
import com.votrip.user.entity.User;
import com.votrip.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * TripService.create's guide-assignment and TripAccessService's membership checks, against a
 * real Postgres running the real V2 migration - same reasoning as UserProvisioningServiceIT:
 * booting the full context under ddl-auto=validate is itself the entity/migration drift check.
 *
 * <p>Firebase is mocked out - this class is about the database, and the suite must run with no
 * service account present.
 */
@SpringBootTest
@Testcontainers
class TripAccessServiceIT {

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
    private TripAccessService tripAccess;

    @Autowired
    private TripRepository trips;

    @Autowired
    private TripMemberRepository tripMembers;

    @Autowired
    private UserRepository users;

    private User creator;
    private User otherUser;

    @BeforeEach
    void setUp() {
        // Child tables first: trip_members -> user_id and trips -> created_by both reference
        // users with no ON DELETE CASCADE.
        tripMembers.deleteAll();
        trips.deleteAll();
        users.deleteAll();

        creator = users.save(User.provisionFromToken("uid-creator", "creator@example.com", "Creator", null));
        otherUser = users.save(User.provisionFromToken("uid-other", "other@example.com", "Other", null));
    }

    private static TripCreateRequest createRequest(String name) {
        return new TripCreateRequest(name, null, null, null, null, null, null);
    }

    @Test
    void creatorBecomesGuideOnCreate() {
        Trip trip = tripService.create(creator, createRequest("Test Trip"));

        assertThat(tripAccess.roleOf(trip.getId(), creator.getId())).contains(TripMemberRole.GUIDE);
        assertThat(tripAccess.isGuide(trip.getId(), creator.getId())).isTrue();
        assertThat(tripAccess.isMember(trip.getId(), creator.getId())).isTrue();
    }

    @Test
    void nonMemberIsDenied() {
        Trip trip = tripService.create(creator, createRequest("Test Trip"));

        assertThat(tripAccess.isMember(trip.getId(), otherUser.getId())).isFalse();
        assertThatThrownBy(() -> tripAccess.requireMembership(trip.getId(), otherUser.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void memberIsAllowedButNotAsGuide() {
        Trip trip = tripService.create(creator, createRequest("Test Trip"));
        tripMembers.save(TripMember.joinedAs(trip.getId(), otherUser.getId(), TripMemberRole.MEMBER));

        assertThat(tripAccess.isMember(trip.getId(), otherUser.getId())).isTrue();
        assertThat(tripAccess.roleOf(trip.getId(), otherUser.getId())).contains(TripMemberRole.MEMBER);
        assertThat(tripAccess.isGuide(trip.getId(), otherUser.getId())).isFalse();
        assertThatThrownBy(() -> tripAccess.requireGuide(trip.getId(), otherUser.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }
}
