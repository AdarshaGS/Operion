package com.operion.parent.api;

import java.time.Duration;

import com.operion.common.ClientIpResolver;
import com.operion.common.SlidingWindowRateLimiter;
import com.operion.common.TooManyRequestsException;
import com.operion.identity.auth.api.ClaimInviteRequest;
import com.operion.identity.auth.api.LoginResponse;
import com.operion.parent.PortalInviteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
	private final ClientIpResolver clientIpResolver;
	/** Per caller address: the claim token is bcrypt-matched against every pending invite, so unthrottled guessing is costly too. */
	private final SlidingWindowRateLimiter claimLimiter = new SlidingWindowRateLimiter(20, Duration.ofMinutes(10), 10_000);

	PortalInviteAuthController(PortalInviteService portalInviteService, ClientIpResolver clientIpResolver) {
		this.portalInviteService = portalInviteService;
		this.clientIpResolver = clientIpResolver;
	}

	@PostMapping("/claim-invite")
	LoginResponse claimInvite(@Valid @RequestBody ClaimInviteRequest request, HttpServletRequest httpRequest) {
		if (!claimLimiter.tryAcquire(clientIpResolver.resolve(httpRequest))) {
			throw new TooManyRequestsException("Too many attempts. Please try again later.");
		}
		return LoginResponse.from(portalInviteService.claim(request.organisationSlug(), request.token(), request.password()));
	}
}
