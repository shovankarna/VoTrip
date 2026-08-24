package com.votrip.user.controller;

import com.votrip.config.FirebaseUserPrincipal;
import com.votrip.user.dto.PingResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness check that also proves the auth path end to end.
 *
 * <p>Reaching this method at all means the Firebase Admin SDK verified the caller's ID token, so
 * returning the resolved identity is enough to confirm the whole chain works. There is no service
 * layer behind it because there is no work to do beyond reading the already-authenticated
 * principal.
 */
@RestController
@RequestMapping("/api/v1")
public class PingController {

    @GetMapping("/ping")
    public PingResponse ping(@AuthenticationPrincipal FirebaseUserPrincipal principal) {
        return new PingResponse(principal.uid(), principal.email());
    }
}
