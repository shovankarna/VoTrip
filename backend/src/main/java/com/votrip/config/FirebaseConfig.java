package com.votrip.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Initialises the Firebase Admin SDK, which this service uses purely to verify the ID tokens the
 * clients send. Firebase proves <em>who</em> a caller is; every <em>what are they allowed to do</em>
 * decision stays in this service (docs/03-TechStack.md §9).
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Bean
    public FirebaseApp firebaseApp(FirebaseProperties properties) throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        if (!StringUtils.hasText(properties.credentialsPath())) {
            throw new IllegalStateException(
                    "FIREBASE_CREDENTIALS_PATH is not set. Point it at your Firebase service account "
                            + "JSON file — there is no local auth bypass (CLAUDE.md).");
        }

        Path credentials = Path.of(properties.credentialsPath());
        if (!Files.isReadable(credentials)) {
            throw new IllegalStateException(
                    "Firebase service account file is not readable at '%s' (from FIREBASE_CREDENTIALS_PATH)."
                            .formatted(credentials.toAbsolutePath()));
        }

        GoogleCredentials googleCredentials;
        try (InputStream in = Files.newInputStream(credentials)) {
            googleCredentials = GoogleCredentials.fromStream(in);
        }

        // Resolve the project explicitly. The Admin SDK would otherwise leave it unset here and
        // fall back to ambient sources later, which makes token audience checks depend on
        // environment rather than on this file. FIREBASE_PROJECT_ID wins if set.
        String projectId = properties.projectId();
        if (!StringUtils.hasText(projectId) && googleCredentials instanceof ServiceAccountCredentials sa) {
            projectId = sa.getProjectId();
        }
        if (!StringUtils.hasText(projectId)) {
            throw new IllegalStateException(
                    "Could not determine the Firebase project id from '%s'. Set FIREBASE_PROJECT_ID."
                            .formatted(credentials.toAbsolutePath()));
        }

        FirebaseApp app =
                FirebaseApp.initializeApp(
                        FirebaseOptions.builder()
                                .setCredentials(googleCredentials)
                                .setProjectId(projectId)
                                .build());
        log.info("Firebase Admin SDK initialised for project '{}'", projectId);
        return app;
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }
}
