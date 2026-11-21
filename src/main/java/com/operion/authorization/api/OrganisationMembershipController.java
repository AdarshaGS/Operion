package com.operion.authorization.api;

import java.util.List;
import java.util.Set;

import com.operion.authorization.HrProfileLookup;
import com.operion.authorization.MembershipService;
import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.authorization.RequirePermission;
import com.operion.common.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Full access control listing/granting - unlike Role's list/get, this is gated end to
 * end since it reveals exactly who has access to what, not just reference/picker data. */
@RestController
@RequestMapping("/api/v1/memberships")
@RequirePermission("MEMBERSHIP_MANAGE")
public class OrganisationMembershipController {

	private final MembershipService membershipService;
	private final OrganisationMembershipRepository membershipRepository;
	private final HrProfileLookup hrProfileLookup;

	public OrganisationMembershipController(MembershipService membershipService, OrganisationMembershipRepository membershipRepository,
			HrProfileLookup hrProfileLookup) {
		this.membershipService = membershipService;
		this.membershipRepository = membershipRepository;
		this.hrProfileLookup = hrProfileLookup;
	}

	@GetMapping
	@RequirePermission("MEMBERSHIP_VIEW")
	public List<MembershipResponse> list() {
		// One query for every person with an HR profile, then a set lookup per row - not a query per member.
		Set<Long> withProfile = hrProfileLookup.personIdsWithProfile();
		return membershipRepository.findAll().stream()
				.map(membership -> MembershipResponse.from(membership, withProfile.contains(membership.getPerson().getId())))
				.toList();
	}

	@PostMapping
	public MembershipResponse grant(@RequestBody GrantMembershipRequest request) {
		return respond(membershipService.grant(request.userId(), request.personId(), request.roleId(), request.campusId(),
				request.departmentId(), request.memberId(), request.joiningDate(), TenantContext.getActorId()));
	}

	@PostMapping("/{id}/revoke")
	public MembershipResponse revoke(@PathVariable Long id) {
		return respond(membershipService.revoke(id));
	}

	private MembershipResponse respond(OrganisationMembership membership) {
		return MembershipResponse.from(membership, hrProfileLookup.hasProfile(membership.getPerson().getId()));
	}
}
