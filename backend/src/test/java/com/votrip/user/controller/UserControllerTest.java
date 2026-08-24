package com.votrip.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.votrip.config.FirebaseUserPrincipal;
import com.votrip.config.RestAccessDeniedHandler;
import com.votrip.config.RestAuthenticationEntryPoint;
import com.votrip.config.SecurityConfig;
import com.votrip.user.entity.User;
import com.votrip.user.service.UserProvisioningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The web layer of GET /api/v1/users/me: that it is behind the same auth boundary as every other
 * endpoint, and that it serves the account resolved from the token rather than anything the client
 * asked for.
 */
@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FirebaseAuth firebaseAuth;

    @MockitoBean
    private UserProvisioningService provisioning;

    private void givenVerifiedToken(String value, String uid, String email) throws Exception {
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn(uid);
        when(token.getEmail()).thenReturn(email);
        when(token.getName()).thenReturn("Test Traveller");
        when(firebaseAuth.verifyIdToken(value)).thenReturn(token);
    }

    @Test
    void returnsTheAccountResolvedFromTheToken() throws Exception {
        givenVerifiedToken("valid-token", "firebase-uid-123", "traveller@example.com");
        User user = User.provisionFromToken(
                "firebase-uid-123", "traveller@example.com", "Test Traveller", null);
        when(provisioning.provision(any(FirebaseUserPrincipal.class))).thenReturn(user);

        mockMvc
                .perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.firebaseUid").value("firebase-uid-123"))
                .andExpect(jsonPath("$.email").value("traveller@example.com"))
                .andExpect(jsonPath("$.displayName").value("Test Traveller"))
                .andExpect(jsonPath("$.isSuperAdmin").value(false))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void rejectsRequestWithoutAuthorizationHeader() throws Exception {
        mockMvc
                .perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.path").value("/api/v1/users/me"));

        // An unauthenticated caller must never reach provisioning - otherwise an anonymous
        // request could create account rows.
        verify(provisioning, never()).provision(any());
    }
}
