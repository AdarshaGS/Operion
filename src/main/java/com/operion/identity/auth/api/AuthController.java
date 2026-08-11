package com.operion.identity.auth.api;

import java.util.List;

import com.operion.authorization.MembershipStatus;
import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.common.TenantContext;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import com.operion.identity.auth.AuthenticationService;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthenticationService authenticationService;
	private final OrganisationMembershipRepository membershipRepository;
	private final UserRepository userRepository;
	private final OrganisationRepository organisationRepository;

	public AuthController(AuthenticationService authenticationService, OrganisationMembershipRepository membershipRepository,
			UserRepository userRepository, OrganisationRepository organisationRepository) {
		this.authenticationService = authenticationService;
		this.membershipRepository = membershipRepository;
		this.userRepository = userRepository;
		this.organisationRepository = organisationRepository;
	}

	@PostMapping("/login")
	public LoginResponse login(@RequestBody LoginRequest request) {
		return LoginResponse.from(authenticationService.login(request.organisationSlug(), request.email(), request.password()));
	}

	/**
	 * Echoes what the interceptor resolved from the caller's token, the same permission
	 * set PermissionInterceptor itself checks requests against (lets the frontend
	 * hide/disable actions instead of only finding out via a 403 on submit), and the
	 * caller's own display identity (person name / active role(s) / org name) so the UI
	 * has something to show for "who am I logged in as" beyond a bare user id.
	 */
	@GetMapping("/me")
	public MeResponse me() {
		Long userId = TenantContext.getActorId();
		Long organisationId = TenantContext.getOrganisationId();

		// findByUserId is tenant-scoped (@TenantId on OrganisationMembership), so this
		// only ever sees memberships in the org the caller's token is scoped to.
		List<OrganisationMembership> activeMemberships = membershipRepository.findByUserId(userId).stream()
				.filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
				.toList();

		// One user maps to one Person within a given org (multiple roles are multiple
		// membership rows sharing that same person, per the identity model) - take the
		// first membership's person as the display name.
		String personName = activeMemberships.stream()
				.findFirst()
				.map(membership -> membership.getPerson().getFirstName() + " " + membership.getPerson().getLastName())
				.orElse(null);
		List<String> roleNames = activeMemberships.stream().map(membership -> membership.getRole().getName()).distinct().toList();

		String email = userRepository.findById(userId).map(User::getEmail).orElse(null);
		String organisationName = organisationRepository.findById(organisationId).map(Organisation::getName).orElse(null);

		return new MeResponse(userId, organisationId, organisationName, email, personName, roleNames,
				membershipRepository.findActivePermissionCodesForUser(userId));
	}
}
