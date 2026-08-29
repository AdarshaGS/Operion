package com.operion.identity.api;

import java.util.List;

import com.operion.authorization.MembershipStatus;
import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.authorization.RequirePermission;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.ProfileChangeRequest;
import com.operion.identity.ProfileChangeRequestRepository;
import com.operion.identity.ProfileChangeRequestStatus;
import com.operion.identity.ProfileChangeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The /me endpoints resolve the caller's own Person from TenantContext.getActorId(),
 * the same membershipRepository.findByUserId(...) lookup AuthController.me() already
 * does - no @RequirePermission on them since a caller acting on their own record needs
 * no special grant, just an authenticated session. The /profile-change-requests
 * endpoints are the staff-side inbox, gated by PROFILE_CHANGE_MANAGE. decidedBy/
 * reviewedBy always comes from TenantContext, never a request body field - see
 * TransferRequestController's identical note.
 */
@RestController
public class ProfileChangeRequestController {

	private final ProfileChangeService profileChangeService;
	private final ProfileChangeRequestRepository profileChangeRequestRepository;
	private final OrganisationMembershipRepository membershipRepository;

	public ProfileChangeRequestController(ProfileChangeService profileChangeService,
			ProfileChangeRequestRepository profileChangeRequestRepository, OrganisationMembershipRepository membershipRepository) {
		this.profileChangeService = profileChangeService;
		this.profileChangeRequestRepository = profileChangeRequestRepository;
		this.membershipRepository = membershipRepository;
	}

	@PostMapping("/api/v1/me/profile-change-requests")
	public ProfileChangeRequestResponse submitOwn(@RequestBody SubmitProfileChangeRequest request) {
		Person person = findCallersPerson();
		ProfileChangeRequest profileChangeRequest =
				profileChangeService.submit(person, request.phone(), request.email(), request.photoUrl(), TenantContext.getActorId());
		return ProfileChangeRequestResponse.from(profileChangeRequest);
	}

	@GetMapping("/api/v1/me/profile-change-requests")
	public List<ProfileChangeRequestResponse> listOwn() {
		Person person = findCallersPerson();
		return profileChangeRequestRepository.findByPersonId(person.getId()).stream().map(ProfileChangeRequestResponse::from).toList();
	}

	@GetMapping("/api/v1/profile-change-requests")
	@RequirePermission("PROFILE_CHANGE_MANAGE")
	public List<ProfileChangeRequestResponse> list(@RequestParam(required = false) String status) {
		ProfileChangeRequestStatus parsedStatus = status != null ? ProfileChangeRequestStatus.valueOf(status) : null;
		List<ProfileChangeRequest> requests = parsedStatus != null
				? profileChangeRequestRepository.findByStatus(parsedStatus)
				: profileChangeRequestRepository.findAll();
		return requests.stream().map(ProfileChangeRequestResponse::from).toList();
	}

	@PostMapping("/api/v1/profile-change-requests/{id}/approve")
	@RequirePermission("PROFILE_CHANGE_MANAGE")
	public ProfileChangeRequestResponse approve(@PathVariable Long id) {
		return ProfileChangeRequestResponse.from(profileChangeService.approve(findRequest(id), TenantContext.getActorId()));
	}

	@PostMapping("/api/v1/profile-change-requests/{id}/reject")
	@RequirePermission("PROFILE_CHANGE_MANAGE")
	public ProfileChangeRequestResponse reject(@PathVariable Long id) {
		return ProfileChangeRequestResponse.from(profileChangeService.reject(findRequest(id), TenantContext.getActorId()));
	}

	private Person findCallersPerson() {
		Long actorId = TenantContext.getActorId();
		return membershipRepository.findByUserId(actorId).stream()
				.filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
				.findFirst()
				.map(OrganisationMembership::getPerson)
				.orElseThrow(() -> new IllegalStateException("No active membership/person for the current caller"));
	}

	private ProfileChangeRequest findRequest(Long id) {
		return profileChangeRequestRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No profile change request with id " + id));
	}
}
