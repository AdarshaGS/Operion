package com.operion.messaging;

import java.util.Comparator;
import java.util.Optional;

import com.operion.authorization.MembershipStatus;
import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.identity.Person;
import org.springframework.stereotype.Component;

/**
 * The one answer to "which Person is this user, in the organisation currently in TenantContext" for
 * messaging, shared by the REST controller and the STOMP interceptor so both always pick the same
 * identity. A user can hold several memberships in an org (one row per role); this takes the ACTIVE
 * ones only and, if there are several, the lowest membership id - a stable rule, where "whichever row
 * the database returns first" is not. Memberships are tenant-filtered, so TenantContext must already
 * be set to the organisation of interest.
 */
@Component
public class MessagingIdentityResolver {

	private final OrganisationMembershipRepository organisationMembershipRepository;

	public MessagingIdentityResolver(OrganisationMembershipRepository organisationMembershipRepository) {
		this.organisationMembershipRepository = organisationMembershipRepository;
	}

	public Optional<Person> findPerson(Long userId) {
		return organisationMembershipRepository.findByUserId(userId).stream()
				.filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
				.min(Comparator.comparing(OrganisationMembership::getId))
				.map(OrganisationMembership::getPerson);
	}

	public Person requirePerson(Long userId) {
		return findPerson(userId).orElseThrow(() -> new IllegalStateException("No active organisation membership for the current user"));
	}
}
