package com.votrip.user.controller;

import com.votrip.config.FirebaseUserPrincipal;
import com.votrip.user.dto.UserResponse;
import com.votrip.user.service.UserProvisioningService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own account.
 *
 * <p>There is no lookup-by-id and no list endpoint (issue #3, Out of scope): the only account a
 * caller can read is the one their verified token resolves to, so there is no resource-level
 * authorization decision to make here yet.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserProvisioningService provisioning;

    public UserController(UserProvisioningService provisioning) {
        this.provisioning = provisioning;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal FirebaseUserPrincipal principal) {
        return UserResponse.from(provisioning.provision(principal));
    }
}
