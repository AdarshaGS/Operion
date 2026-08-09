package com.operion.identity.auth.api;

import com.operion.common.TenantContext;
import com.operion.identity.auth.AuthenticationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthenticationService authenticationService;

	public AuthController(AuthenticationService authenticationService) {
		this.authenticationService = authenticationService;
	}

	@PostMapping("/login")
	public LoginResponse login(@RequestBody LoginRequest request) {
		return LoginResponse.from(authenticationService.login(request.organisationSlug(), request.email(), request.password()));
	}

	/** Echoes what the interceptor resolved from the caller's token - useful to confirm it's wired correctly. */
	@GetMapping("/me")
	public MeResponse me() {
		return new MeResponse(TenantContext.getActorId(), TenantContext.getOrganisationId());
	}
}
