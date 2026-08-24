package com.votrip.user.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.votrip.config.RestAccessDeniedHandler;
import com.votrip.config.RestAuthenticationEntryPoint;
import com.votrip.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Covers the auth path, which is the entire point of the walking skeleton: a caller with no token
 * or a rejected token must never reach the endpoint, and a verified caller must get back the
 * identity the server resolved rather than anything the client claimed about itself.
 *
 * <p>FirebaseAuth is mocked so the suite needs neither real credentials nor a database, which is
 * what lets {@code ./mvnw verify} run in CI (CONTRIBUTING.md).
 */
@WebMvcTest(controllers = PingController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class PingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FirebaseAuth firebaseAuth;

    @Test
    void returnsResolvedIdentityWhenTokenIsValid() throws Exception {
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn("firebase-uid-123");
        when(token.getEmail()).thenReturn("traveller@example.com");
        when(token.isEmailVerified()).thenReturn(true);
        when(token.getName()).thenReturn("Test Traveller");
        when(firebaseAuth.verifyIdToken("valid-token")).thenReturn(token);

        mockMvc
                .perform(get("/api/v1/ping").header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value("firebase-uid-123"))
                .andExpect(jsonPath("$.email").value("traveller@example.com"));
    }

    @Test
    void returnsNullEmailWhenTokenCarriesNoEmailClaim() throws Exception {
        // Phone-number and anonymous Firebase providers issue tokens with no email claim.
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn("firebase-uid-456");
        when(token.getEmail()).thenReturn(null);
        when(firebaseAuth.verifyIdToken("phone-token")).thenReturn(token);

        mockMvc
                .perform(get("/api/v1/ping").header(HttpHeaders.AUTHORIZATION, "Bearer phone-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value("firebase-uid-456"))
                .andExpect(jsonPath("$.email").isEmpty());
    }

    @Test
    void rejectsRequestWithoutAuthorizationHeader() throws Exception {
        mockMvc
                .perform(get("/api/v1/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.path").value("/api/v1/ping"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void rejectsExpiredOrRevokedToken() throws Exception {
        FirebaseAuthException rejected = mock(FirebaseAuthException.class);
        when(rejected.getMessage()).thenReturn("Firebase ID token has expired");
        when(firebaseAuth.verifyIdToken("expired-token")).thenThrow(rejected);

        mockMvc
                .perform(get("/api/v1/ping").header(HttpHeaders.AUTHORIZATION, "Bearer expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void rejectsMalformedToken() throws Exception {
        when(firebaseAuth.verifyIdToken(anyString()))
                .thenThrow(new IllegalArgumentException("Failed to parse Firebase ID token"));

        mockMvc
                .perform(get("/api/v1/ping").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
