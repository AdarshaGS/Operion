package com.operion.portal.api;

import com.operion.common.TenantContext;
import com.operion.portal.MeResponse;
import com.operion.portal.PortalIdentityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * No {@code @RequirePermission} - reachable by any authenticated org member, same
 * convention as {@code AuthController.me()}: the whole point is telling a just-logged-in
 * session where to route itself, before any permission-gated screen is reachable.
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

	private final PortalIdentityService portalIdentityService;

	public MeController(PortalIdentityService portalIdentityService) {
		this.portalIdentityService = portalIdentityService;
	}

	@GetMapping
	public MeResponse me() {
		return portalIdentityService.resolve(TenantContext.getActorId());
	}
}
