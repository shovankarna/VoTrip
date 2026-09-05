package com.votrip.trip.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.votrip.common.exception.ApiException;
import com.votrip.config.RestAccessDeniedHandler;
import com.votrip.config.RestAuthenticationEntryPoint;
import com.votrip.config.SecurityConfig;
import com.votrip.trip.entity.Trip;
import com.votrip.trip.service.TripService;
import com.votrip.user.entity.User;
import com.votrip.user.service.UserProvisioningService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The web layer of the trip endpoints: that a 403/404 thrown by TripService reaches the client
 * through the same shared error shape as everything else, and that request validation is wired
 * up. Every actual authorization/business rule is TripServiceIT's job - this class is about the
 * controller boundary, not re-proving those rules through MockMvc.
 */
@WebMvcTest(controllers = TripController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class TripControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FirebaseAuth firebaseAuth;

    @MockitoBean
    private UserProvisioningService provisioning;

    @MockitoBean
    private TripService tripService;

    private void givenVerifiedToken(String value, String uid) throws Exception {
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn(uid);
        when(firebaseAuth.verifyIdToken(value)).thenReturn(token);
    }

    private User caller() {
        return User.provisionFromToken("firebase-uid-123", "traveller@example.com", "Traveller", null);
    }

    @Test
    void createReturns201() throws Exception {
        givenVerifiedToken("valid-token", "firebase-uid-123");
        User caller = caller();
        when(provisioning.provision(any())).thenReturn(caller);
        Trip trip = Trip.create("Bali Trip", null, null, null, null, null, null, caller.getId(), "ABCD1234");
        when(tripService.create(eq(caller), any())).thenReturn(trip);

        mockMvc.perform(post("/api/v1/trips")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bali Trip\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Bali Trip"))
                .andExpect(jsonPath("$.myRole").value("GUIDE"))
                .andExpect(jsonPath("$.inviteCode").value("ABCD1234"));
    }

    @Test
    void createRejectsBlankNameWith400() throws Exception {
        givenVerifiedToken("valid-token", "firebase-uid-123");
        when(provisioning.provision(any())).thenReturn(caller());

        mockMvc.perform(post("/api/v1/trips")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getMapsAccessDeniedFromServiceTo403() throws Exception {
        givenVerifiedToken("valid-token", "firebase-uid-123");
        when(provisioning.provision(any())).thenReturn(caller());
        UUID tripId = UUID.randomUUID();
        when(tripService.getVisibleTrip(eq(tripId), any()))
                .thenThrow(new AccessDeniedException("Not a member of this trip."));

        mockMvc.perform(get("/api/v1/trips/" + tripId).header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void getMapsTripNotFoundFromServiceTo404() throws Exception {
        givenVerifiedToken("valid-token", "firebase-uid-123");
        when(provisioning.provision(any())).thenReturn(caller());
        UUID tripId = UUID.randomUUID();
        when(tripService.getVisibleTrip(eq(tripId), any()))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "No such trip."));

        mockMvc.perform(get("/api/v1/trips/" + tripId).header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
    }

    @Test
    void patchSucceedsForAGuide() throws Exception {
        givenVerifiedToken("valid-token", "firebase-uid-123");
        User caller = caller();
        when(provisioning.provision(any())).thenReturn(caller);
        UUID tripId = UUID.randomUUID();
        Trip updated = Trip.create("Renamed", null, null, null, null, null, null, caller.getId(), "ABCD1234");
        when(tripService.update(eq(tripId), any(), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/trips/" + tripId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
    }

    @Test
    void rejectsRequestWithoutAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/trips")).andExpect(status().isUnauthorized());
    }
}
