package com.operion.identity.api;

import java.util.List;

import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.authorization.RequirePermission;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import com.operion.identity.UserService;
import com.operion.identity.UserStatus;
import com.operion.identity.auth.EmailVerificationService;
import com.operion.identity.auth.StaffInviteService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Creates login identities for people who need to sign in - a precursor to granting an
 * OrganisationMembership, since a membership requires an existing User. Gated the same as
 * membership management: creating a login is part of the "give someone access" action, not
 * a separate concern. User is deliberately global (see User's own javadoc) - the unique
 * email constraint means re-using an email that already has a login elsewhere surfaces as
 * a plain 409, same as every other duplicate-key case in this codebase.
 *
 * list()/get() scope to users who hold at least one OrganisationMembership (any status,
 * not just ACTIVE - a revoked membership still means "this user has history with this
 * org") in the caller's own org. User itself carries no organisation_id to filter on
 * directly (that's the whole point of it being global), so the only tenant boundary
 * available is via OrganisationMembership, which is @TenantId-scoped. Without this,
 * every login's email on the entire platform - across every organisation - would be
 * visible to any caller with MEMBERSHIP_MANAGE in their own org alone.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequirePermission("MEMBERSHIP_MANAGE")
public class UserController {

	private final UserRepository userRepository;
	private final OrganisationMembershipRepository membershipRepository;
	private final PasswordEncoder passwordEncoder;
	private final UserService userService;
	private final StaffInviteService staffInviteService;
	private final EmailVerificationService emailVerificationService;

	public UserController(UserRepository userRepository, OrganisationMembershipRepository membershipRepository,
			PasswordEncoder passwordEncoder, UserService userService, StaffInviteService staffInviteService,
			EmailVerificationService emailVerificationService) {
		this.userRepository = userRepository;
		this.membershipRepository = membershipRepository;
		this.passwordEncoder = passwordEncoder;
		this.userService = userService;
		this.staffInviteService = staffInviteService;
		this.emailVerificationService = emailVerificationService;
	}

	@PostMapping
	public UserResponse create(@RequestBody CreateUserRequest request) {
		User user = new User(request.email(), request.phone(), passwordEncoder.encode(request.password()));
		user = userRepository.save(user);
		emailVerificationService.issue(user.getId());
		return UserResponse.from(user);
	}

	/** Preferred path for onboarding a new staff login - see StaffInviteService. Unlike
	 * create() above, the admin never sees or sets the real password. Also issues an email
	 * verification token, same as create() - this is the primary user-creation path now
	 * that it exists, so #48's "on create" trigger has to fire here too, not just from the
	 * older direct-password endpoint. */
	@PostMapping("/invite")
	public StaffInviteResponse invite(@RequestBody InviteUserRequest request) {
		StaffInviteService.IssuedInvite invite = staffInviteService.issue(request.email(), request.phone());
		emailVerificationService.issue(invite.userId());
		return new StaffInviteResponse(invite.userId(), invite.inviteId(), invite.rawToken(), invite.expiresAt(), invite.emailSent());
	}

	@GetMapping
	public List<UserResponse> list() {
		return membershipRepository.findAll().stream()
				.map(OrganisationMembership::getUser)
				.distinct()
				.map(UserResponse::from)
				.toList();
	}

	@GetMapping("/{id}")
	public UserResponse get(@PathVariable Long id) {
		boolean belongsToCurrentOrg = membershipRepository.findAll().stream()
				.anyMatch(membership -> membership.getUser().getId().equals(id));
		if (!belongsToCurrentOrg) {
			throw new IllegalArgumentException("No user with id " + id);
		}
		return UserResponse.from(userRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No user with id " + id)));
	}

	@PutMapping("/{id}")
	public UserResponse update(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
		return UserResponse.from(userService.update(id, request.email(), request.phone()));
	}

	/** Also the deactivate path (status=DISABLED) - see UserService.changeStatus. */
	@PostMapping("/{id}/status")
	public UserResponse changeStatus(@PathVariable Long id, @RequestBody ChangeUserStatusRequest request) {
		return UserResponse.from(userService.changeStatus(id, UserStatus.valueOf(request.status())));
	}
}
