package com.votrip.trip.controller;

import com.votrip.config.FirebaseUserPrincipal;
import com.votrip.trip.dto.InviteCodeResponse;
import com.votrip.trip.dto.TripCreateRequest;
import com.votrip.trip.dto.TripJoinRequest;
import com.votrip.trip.dto.TripResponse;
import com.votrip.trip.dto.TripUpdateRequest;
import com.votrip.trip.entity.Trip;
import com.votrip.trip.entity.TripMemberRole;
import com.votrip.trip.service.TripMembershipView;
import com.votrip.trip.service.TripService;
import com.votrip.user.entity.User;
import com.votrip.user.service.UserProvisioningService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trip create/read/update and the invite-code join flow.
 *
 * <p>Every method resolves the caller's own {@link User} first (same pattern as
 * {@code UserController.me()}), then delegates straight to {@link TripService} - all
 * authorization and existence decisions live there (via {@link com.votrip.trip.service.TripAccessService}),
 * not here. No trip lifecycle status transitions, member removal, or explicit email/phone invites
 * yet - those are a later chunk.
 */
@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

    private final TripService tripService;
    private final UserProvisioningService provisioning;

    public TripController(TripService tripService, UserProvisioningService provisioning) {
        this.tripService = tripService;
        this.provisioning = provisioning;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripResponse create(
            @AuthenticationPrincipal FirebaseUserPrincipal principal, @Valid @RequestBody TripCreateRequest request) {
        User caller = provisioning.provision(principal);
        Trip trip = tripService.create(caller, request);
        // The creator is always the trip's guide - see TripService.create.
        return TripResponse.from(trip, TripMemberRole.GUIDE);
    }

    @GetMapping
    public List<TripResponse> list(@AuthenticationPrincipal FirebaseUserPrincipal principal) {
        User caller = provisioning.provision(principal);
        return tripService.findMemberTrips(caller.getId()).stream()
                .map(view -> TripResponse.from(view.trip(), view.role()))
                .toList();
    }

    @GetMapping("/{id}")
    public TripResponse get(@AuthenticationPrincipal FirebaseUserPrincipal principal, @PathVariable UUID id) {
        User caller = provisioning.provision(principal);
        TripMembershipView view = tripService.getVisibleTrip(id, caller.getId());
        return TripResponse.from(view.trip(), view.role());
    }

    @PatchMapping("/{id}")
    public TripResponse update(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody TripUpdateRequest request) {
        User caller = provisioning.provision(principal);
        Trip trip = tripService.update(id, caller.getId(), request);
        // update() only succeeds if requireGuide passed - see TripService.update.
        return TripResponse.from(trip, TripMemberRole.GUIDE);
    }

    @PostMapping("/join")
    public TripResponse join(
            @AuthenticationPrincipal FirebaseUserPrincipal principal, @Valid @RequestBody TripJoinRequest request) {
        User caller = provisioning.provision(principal);
        TripMembershipView view = tripService.joinByInviteCode(caller, request.inviteCode());
        return TripResponse.from(view.trip(), view.role());
    }

    /** Guide-only dedicated lookup - see TripResponse's note on why inviteCode isn't in the general shape for members. */
    @GetMapping("/{id}/invite-code")
    public InviteCodeResponse inviteCode(
            @AuthenticationPrincipal FirebaseUserPrincipal principal, @PathVariable UUID id) {
        User caller = provisioning.provision(principal);
        return new InviteCodeResponse(tripService.getInviteCode(id, caller.getId()));
    }

    @PostMapping("/{id}/invite-code/regenerate")
    public TripResponse regenerateInviteCode(
            @AuthenticationPrincipal FirebaseUserPrincipal principal, @PathVariable UUID id) {
        User caller = provisioning.provision(principal);
        Trip trip = tripService.regenerateInviteCode(id, caller.getId());
        return TripResponse.from(trip, TripMemberRole.GUIDE);
    }
}
