package com.votrip.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Firebase Admin SDK settings.
 *
 * <p>{@code credentialsPath} is required and is supplied through the
 * {@code FIREBASE_CREDENTIALS_PATH} environment variable — the service account JSON is never
 * committed to the repo (docs/03-TechStack.md §11). {@code projectId} is optional: when blank the
 * Admin SDK derives the project from the service account file itself.
 */
@ConfigurationProperties(prefix = "votrip.firebase")
public record FirebaseProperties(String credentialsPath, String projectId) {
}
