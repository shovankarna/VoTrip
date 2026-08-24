package com.votrip.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.votrip.config.FirebaseUserPrincipal;
import com.votrip.user.entity.User;
import com.votrip.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Auto-provisioning against a real Postgres running the real V1 migration.
 *
 * <p>Booting the full context with {@code ddl-auto=validate} is itself an assertion: if the entity
 * and the migration disagree on a column, every test in this class fails to start. That is the
 * drift check the Data Layer Convention asks for, not a separate test.
 *
 * <p>Firebase is mocked out - this class is about the database, and the suite must run with no
 * service account present.
 */
@SpringBootTest
@Testcontainers
class UserProvisioningServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @MockitoBean
    private FirebaseApp firebaseApp;

    @MockitoBean
    private FirebaseAuth firebaseAuth;

    @Autowired
    private UserProvisioningService provisioning;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        users.deleteAll();
    }

    private static FirebaseUserPrincipal principal(String uid, String email) {
        return new FirebaseUserPrincipal(uid, email, true, "Test Traveller", "https://cdn/a.png");
    }

    @Test
    void newUsersDefaultToActive() {
        User provisioned = provisioning.provision(principal("uid-new", "new@example.com"));

        assertThat(provisioned.isActive()).isTrue();
        assertThat(users.findById(provisioned.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void databaseDefaultsIsActiveToTrueWhenTheColumnIsNotSupplied() {
        // Proves the DEFAULT TRUE in V1 itself, independently of what the entity sends.
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO users (id, firebase_uid, created_at) VALUES (?, ?, NOW())",
                id, "uid-raw-insert");

        Boolean active = jdbc.queryForObject("SELECT is_active FROM users WHERE id = ?", Boolean.class, id);
        assertThat(active).isTrue();
    }

    @Test
    void provisionsFromTokenClaimsOnFirstRequest() {
        User provisioned = provisioning.provision(principal("uid-first", "first@example.com"));

        assertThat(provisioned.getId()).isNotNull();
        assertThat(provisioned.getFirebaseUid()).isEqualTo("uid-first");
        assertThat(provisioned.getEmail()).isEqualTo("first@example.com");
        assertThat(provisioned.getDisplayName()).isEqualTo("Test Traveller");
        assertThat(provisioned.getAvatarUrl()).isEqualTo("https://cdn/a.png");
        assertThat(provisioned.getCreatedAt()).isNotNull();
        // Never self-elevating: a caller cannot provision themselves as a super admin.
        assertThat(provisioned.isSuperAdmin()).isFalse();
    }

    @Test
    void secondRequestReusesTheSameRowRatherThanCreatingADuplicate() {
        User first = provisioning.provision(principal("uid-repeat", "repeat@example.com"));
        User second = provisioning.provision(principal("uid-repeat", "repeat@example.com"));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void concurrentFirstRequestsForTheSameUserProduceExactlyOneRow() throws Exception {
        // FR-4.4 concurrency: several first requests can land at once. The unique index on
        // firebase_uid makes all but one insert lose; the losers must re-read the winner's row
        // rather than surfacing a 500 for a race the caller did not cause.
        int threads = 8;
        CyclicBarrier startTogether = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<UUID>> jobs = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                jobs.add(() -> {
                    startTogether.await();
                    return provisioning.provision(principal("uid-race", "race@example.com")).getId();
                });
            }

            List<UUID> ids = new java.util.ArrayList<>();
            for (Future<UUID> f : pool.invokeAll(jobs)) {
                ids.add(f.get());
            }

            assertThat(users.count()).isEqualTo(1);
            assertThat(ids).doesNotContainNull().containsOnly(ids.get(0));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void emailIsOptionalForProvidersThatIssueNoEmailClaim() {
        // Phone-number and anonymous providers issue tokens with no email claim.
        User provisioned = provisioning.provision(
                new FirebaseUserPrincipal("uid-phone", null, false, null, null));

        assertThat(provisioned.getEmail()).isNull();
        assertThat(provisioned.isActive()).isTrue();
    }
}
