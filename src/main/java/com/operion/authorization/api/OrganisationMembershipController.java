package com.operion.authorization.api;

import java.util.List;

import com.operion.authorization.MembershipService;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.authorization.RequirePermission;
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

	public OrganisationMembershipController(MembershipService membershipService, OrganisationMembershipRepository membershipRepository) {
		this.membershipService = membershipService;
		this.membershipRepository = membershipRepository;
	}

	@GetMapping
	public List<MembershipResponse> list() {
		return membershipRepository.findAll().stream().map(MembershipResponse::from).toList();
	}

	@PostMapping
	public MembershipResponse grant(@RequestBody GrantMembershipRequest request) {
		return MembershipResponse.from(
				membershipService.grant(request.userId(), request.personId(), request.roleId(), request.campusId(), request.departmentId()));
	}

	@PostMapping("/{id}/revoke")
	public MembershipResponse revoke(@PathVariable Long id) {
		return MembershipResponse.from(membershipService.revoke(id));
	}
}
