package com.votrip.user.dto;

import com.votrip.user.entity.User;
import java.time.Instant;
import java.util.UUID;

/**
 * The account shape GET /api/v1/users/me returns.
 *
 * <p>Mirrors the USER entity from docs/02-SRS-ERD.md section 5. {@code isActive} is exposed
 * read-only - nothing sets it yet (issue #3, Out of scope) - so a client can render a deactivated
 * account correctly once deactivation exists, without a contract change.
 */
public record UserResponse(
        UUID id,
        String firebaseUid,
        String email,
        String displayName,
        String phone,
        String avatarUrl,
        boolean isSuperAdmin,
        boolean isActive,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirebaseUid(),
                user.getEmail(),
                user.getDisplayName(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.isSuperAdmin(),
                user.isActive(),
                user.getCreatedAt());
    }
}
