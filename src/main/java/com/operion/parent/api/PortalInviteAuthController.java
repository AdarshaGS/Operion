package com.operion.parent.api;

import com.operion.identity.auth.api.ClaimInviteRequest;
import com.operion.identity.auth.api.LoginResponse;
import com.operion.parent.PortalInviteService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Split out of AuthController so core (com.operion.identity) never depends on the
 * School-vertical PortalInviteService (see ai-context/platform-boundaries.md and
 * ArchitectureBoundaryTest) - same URL, same public/unauthenticated trust tier as
 * /api/v1/auth/login, still declared under the /api/v1/auth prefix. See
 * PortalInviteService.claim().
 */
@RestController
@RequestMapping("/api/v1/auth")
class PortalInviteAuthController {

	private final PortalInviteService portalInviteService;

	PortalInviteAuthController(PortalInviteService portalInviteService) {
		this.portalInviteService = portalInviteService;
	}

	@PostMapping("/claim-invite")
	LoginResponse claimInvite(@RequestBody ClaimInviteRequest request) {
		return LoginResponse.from(portalInviteService.claim(request.organisationSlug(), request.token(), request.password()));
	}
}
