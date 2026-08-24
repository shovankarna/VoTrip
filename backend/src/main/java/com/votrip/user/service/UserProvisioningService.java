package com.votrip.user.service;

import com.votrip.config.FirebaseUserPrincipal;
import com.votrip.user.entity.User;
import com.votrip.user.repository.UserRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Turns a verified Firebase identity into a VoTrip account row, creating it on first sight.
 *
 * <p>Every field is taken from the verified token. Nothing a client puts in a request body reaches
 * this class, which is what stops a caller from provisioning themselves with someone else's email
 * or an elevated flag.
 */
@Service
public class UserProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(UserProvisioningService.class);

    private final UserRepository users;

    /**
     * Each step runs in its own transaction, and that is load-bearing rather than incidental.
     * When two first requests race, one insert loses on the {@code firebase_uid} unique index;
     * Postgres then aborts that transaction and rejects every later statement in it with
     * "current transaction is aborted". Re-reading the winner's row therefore has to happen in a
     * fresh transaction, after the failed one has rolled back. REQUIRES_NEW also keeps the losing
     * insert from poisoning a caller's enclosing transaction.
     */
    private final TransactionTemplate inItsOwnTransaction;

    public UserProvisioningService(UserRepository users, PlatformTransactionManager transactions) {
        this.users = users;
        this.inItsOwnTransaction = new TransactionTemplate(transactions);
        this.inItsOwnTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Returns the account for this principal, creating it if this is their first authenticated
     * request. Concurrent first requests all converge on a single row.
     */
    public User provision(FirebaseUserPrincipal principal) {
        Optional<User> existing = findByUid(principal.uid());
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            User saved = inItsOwnTransaction.execute(status -> users.saveAndFlush(
                    User.provisionFromToken(
                            principal.uid(),
                            principal.email(),
                            principal.displayName(),
                            principal.picture())));
            log.info("Provisioned new user {} for Firebase uid {}", saved.getId(), saved.getFirebaseUid());
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Lost the race against a concurrent first request - the winner's row is authoritative.
            log.debug("Concurrent provisioning for uid {}, re-reading", principal.uid());
            return findByUid(principal.uid()).orElseThrow(() -> e);
        }
    }

    private Optional<User> findByUid(String firebaseUid) {
        return inItsOwnTransaction.execute(status -> users.findByFirebaseUid(firebaseUid));
    }
}
