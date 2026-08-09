package com.operion.identity.auth;

import com.operion.authorization.MembershipStatus;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.common.TenantContext;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

	private final OrganisationRepository organisationRepository;
	private final UserRepository userRepository;
	private final OrganisationMembershipRepository membershipRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthenticationService(OrganisationRepository organisationRepository, UserRepository userRepository,
			OrganisationMembershipRepository membershipRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.organisationRepository = organisationRepository;
		this.userRepository = userRepository;
		this.membershipRepository = membershipRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	public LoginResult login(String organisationSlug, String email, String rawPassword) {
		// Deliberately the same error for "no such org", "no such user", "wrong password" and
		// "no membership" - distinguishing them lets an attacker enumerate valid slugs/emails.
		AuthenticationFailedException failure = new AuthenticationFailedException("Invalid organisation, email or password");

		Organisation organisation = organisationRepository.findBySlug(organisationSlug).orElseThrow(() -> failure);
		User user = userRepository.findByEmail(email).orElseThrow(() -> failure);

		if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
			throw failure;
		}

		// OrganisationMembership is tenant-scoped (@TenantId) - login is the one place that has
		// to resolve *which* tenant before it can query tenant-scoped data at all, so TenantContext
		// must be set here first or this lookup silently filters to "no tenant" and finds nothing.
		TenantContext.set(organisation.getId(), user.getId());
		boolean hasActiveMembership;
		try {
			hasActiveMembership = membershipRepository.findByUserId(user.getId()).stream()
					.anyMatch(membership -> membership.getStatus() == MembershipStatus.ACTIVE);
		} finally {
			TenantContext.clear();
		}
		if (!hasActiveMembership) {
			throw failure;
		}

		JwtService.IssuedToken issued = jwtService.issue(user.getId(), organisation.getId());
		return new LoginResult(issued.token(), issued.expiresAt(), user.getId(), organisation.getId());
	}
}
