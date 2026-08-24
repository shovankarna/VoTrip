package com.votrip.user.repository;

import com.votrip.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Resolves the account behind a verified Firebase token. Backed by the unique index. */
    Optional<User> findByFirebaseUid(String firebaseUid);
}
