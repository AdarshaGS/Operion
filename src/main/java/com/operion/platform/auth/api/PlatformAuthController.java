package com.operion.platform.auth.api;

import com.operion.common.TenantContext;
import com.operion.platform.auth.PlatformAuthenticationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/auth")
public class PlatformAuthController {

	private final PlatformAuthenticationService platformAuthenticationService;

	public PlatformAuthController(PlatformAuthenticationService platformAuthenticationService) {
		this.platformAuthenticationService = platformAuthenticationService;
	}

	@PostMapping("/login")
	public PlatformLoginResponse login(@RequestBody PlatformLoginRequest request) {
		return PlatformLoginResponse.from(platformAuthenticationService.login(request.email(), request.password()));
	}

	@GetMapping("/me")
	public PlatformMeResponse me() {
		return new PlatformMeResponse(TenantContext.getActorId());
	}
}
