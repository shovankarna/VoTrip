package com.votrip.config;

import com.google.firebase.auth.FirebaseToken;
import java.security.Principal;

/**
 * The verified identity behind a request. Deliberately carries identity only — no roles.
 * Roles in VoTrip are scoped per trip via {@code TRIP_MEMBER}, never global to a user
 * (docs/02-SRS-ERD.md §2), so there is nothing role-shaped to put here.
 */
public record FirebaseUserPrincipal(String uid, String email, boolean emailVerified, String displayName)
        implements Principal {

    public static FirebaseUserPrincipal from(FirebaseToken token) {
        return new FirebaseUserPrincipal(
                token.getUid(), token.getEmail(), token.isEmailVerified(), token.getName());
    }

    /** {@code Authentication.getName()} resolves to the Firebase UID. */
    @Override
    public String getName() {
        return uid;
    }
}
