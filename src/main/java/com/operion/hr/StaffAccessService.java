package com.operion.hr;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.operion.authorization.MemberStatus;
import com.operion.authorization.MembershipService;
import com.operion.authorization.MembershipStatus;
import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.authorization.PermissionChecker;
import com.operion.authorization.Role;
import com.operion.hr.api.StaffAccessRequest;
import com.operion.hr.api.StaffAccessSummary;
import com.operion.identity.Person;
import com.operion.identity.UserRepository;
import com.operion.identity.auth.EmailVerificationService;
import com.operion.identity.auth.RefreshTokenService;
import com.operion.identity.auth.StaffInviteService;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Department;
import com.operion.organisation.Designation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one-way link from Staff to Members: a staff member may OPTIONALLY be given a login (User + OrganisationMembership
 * on their Person). Nothing here makes staff creation imply access, nor requires a member to have a staff profile -
 * owners, portal users and external accountants stay members with no HR profile.
 *
 * Everything that writes lives in one {@code @Transactional} method per use case, so a staff profile can never be
 * left behind by a failed access grant (and the reverse for an exit). The invite email goes out inside the issue()
 * call, before commit, so each use case validates everything that could fail afterwards (role, campus, duplicate email)
 * BEFORE issuing the invite - the only failures left after that point are infrastructure errors, and those roll the
 * whole unit back (the recipient would then hold a link to a login that no longer exists).
 *
 * Callers must have set TenantContext before the transaction opens (the controller path does, via the JWT interceptor).
 */
@Service
public class StaffAccessService {

	private final HrService hrService;
	private final MembershipService membershipService;
	private final OrganisationMembershipRepository membershipRepository;
	private final StaffInviteService staffInviteService;
	private final EmailVerificationService emailVerificationService;
	private final RefreshTokenService refreshTokenService;
	private final UserRepository userRepository;
	private final CampusRepository campusRepository;
	private final PermissionChecker permissionChecker;

	public StaffAccessService(HrService hrService, MembershipService membershipService,
			OrganisationMembershipRepository membershipRepository, StaffInviteService staffInviteService,
			EmailVerificationService emailVerificationService, RefreshTokenService refreshTokenService, UserRepository userRepository,
			CampusRepository campusRepository, PermissionChecker permissionChecker) {
		this.hrService = hrService;
		this.membershipService = membershipService;
		this.membershipRepository = membershipRepository;
		this.staffInviteService = staffInviteService;
		this.emailVerificationService = emailVerificationService;
		this.refreshTokenService = refreshTokenService;
		this.userRepository = userRepository;
		this.campusRepository = campusRepository;
		this.permissionChecker = permissionChecker;
	}

	/** A new staff profile and its login, or neither. {@code reportingManagerId} (nullable) is resolved by HrService inside this
	 * transaction before the profile is written, so a bad id leaves nothing behind. */
	@Transactional
	public AccessResult createStaffWithAccess(Person person, Campus campus, String employeeCode, Designation designation,
			Department department, LocalDate dateOfJoining, EmploymentType employmentType, Long reportingManagerId,
			StaffAccessRequest access, Long actorId) {
		requireMembershipManage(actorId);
		// Fail fast on the access half before the profile is written (the transaction would roll it back anyway).
		requireNoActiveAccess(person);
		Role role = membershipService.requireGrantableRole(access.roleId(), actorId);
		Long campusId = resolveCampusId(access, campus);
		requireEmailFree(access.email());
		requirePhoneFree(normalisedPhone(access));

		StaffProfile staffProfile = hrService.createStaffProfile(person, campus, employeeCode, designation, department, dateOfJoining,
				employmentType, reportingManagerId);
		return issueAccess(staffProfile, role, campusId, access, actorId);
	}

	/** Gives an existing, current staff member a login. 409 when they already have an active one or have left. */
	@Transactional
	public AccessResult giveAccess(StaffProfile staffProfile, StaffAccessRequest access, Long actorId) {
		requireMembershipManage(actorId);
		if (staffProfile.getStatus() == StaffProfileStatus.RESIGNED || staffProfile.getStatus() == StaffProfileStatus.TERMINATED) {
			throw new IllegalStateException("This staff member has left (" + staffProfile.getStatus()
					+ ") and cannot be given login access");
		}
		requireNoActiveAccess(staffProfile.getPerson());
		Role role = membershipService.requireGrantableRole(access.roleId(), actorId);
		Long campusId = resolveCampusId(access, staffProfile.getCampus());
		requireEmailFree(access.email());
		requirePhoneFree(normalisedPhone(access));
		return issueAccess(staffProfile, role, campusId, access, actorId);
	}

	/**
	 * Records the exit AND withdraws the person's login, atomically. Effect of revoking (reused, not new): the JWT interceptor
	 * rejects any access token whose user has no ACTIVE membership, and login() and refresh() both demand one - so every
	 * session ends at its next request. The user's refresh tokens are additionally revoked so none lingers as live data, and
	 * an invite they never claimed is burned (and its PENDING login disabled) so the emailed link cannot bring the account to life.
	 *
	 * The organisation Owner cannot be exited: MembershipService.revoke refuses to strand an org with no Owner, and silently
	 * skipping the revoke would leave a departed person with the keys to the org. So the whole exit is refused (409) and
	 * nothing is written - transfer ownership/appoint another Owner first. The same 409 applies to the last active Org Admin
	 * (another admin only counts while their login is ACTIVE). Both are checked BEFORE the exit is recorded.
	 * A staff member with no membership exits exactly as before.
	 */
	@Transactional
	public StaffExit recordExit(StaffProfile staffProfile, StaffExitType exitType, LocalDate exitDate, String reason, Long initiatedBy) {
		List<OrganisationMembership> active = membershipRepository.findByPersonId(staffProfile.getPerson().getId()).stream()
				.filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
				.toList();
		if (active.stream().anyMatch(OrganisationMembership::isOwner)) {
			throw new IllegalStateException("This staff member is the organisation Owner, whose access cannot be revoked - "
					+ "the exit was not recorded");
		}
		membershipService.requireRevocable(active);

		StaffExit exit = hrService.recordExit(staffProfile, exitType, exitDate, reason, initiatedBy);

		for (OrganisationMembership membership : active) {
			membershipService.revoke(membership.getId());
		}
		Set<Long> userIds = active.stream().map(membership -> membership.getUser().getId()).collect(Collectors.toSet());
		for (Long userId : userIds) {
			boolean stillHasAccess = membershipRepository.findByUserId(userId).stream()
					.anyMatch(membership -> membership.getStatus() == MembershipStatus.ACTIVE);
			if (!stillHasAccess) {
				refreshTokenService.revokeAllForUser(userId);
				staffInviteService.withdrawUnclaimed(userId);
			}
		}
		return exit;
	}

	/** Access status for one person. */
	@Transactional(readOnly = true)
	public StaffAccessSummary summarise(Long personId) {
		return summariseAll(List.of(personId)).getOrDefault(personId, StaffAccessSummary.NONE);
	}

	/** Access status for many people with a single query (the staff roster) - people with no membership are absent from the map. */
	@Transactional(readOnly = true)
	public Map<Long, StaffAccessSummary> summariseAll(Collection<Long> personIds) {
		if (personIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, List<OrganisationMembership>> byPerson = membershipRepository.findByPersonIdIn(personIds).stream()
				.collect(Collectors.groupingBy(membership -> membership.getPerson().getId()));
		Map<Long, StaffAccessSummary> result = new HashMap<>();
		byPerson.forEach((personId, memberships) -> result.put(personId, summarise(memberships)));
		return result;
	}

	/** An ACTIVE membership wins over a revoked one; among equals the latest. */
	private static StaffAccessSummary summarise(List<OrganisationMembership> memberships) {
		OrganisationMembership chosen = memberships.stream()
				.max(Comparator.<OrganisationMembership, Boolean>comparing(m -> m.getStatus() == MembershipStatus.ACTIVE)
						.thenComparing(OrganisationMembership::getId))
				.orElseThrow();
		boolean hasAccess = chosen.getStatus() == MembershipStatus.ACTIVE;
		// A revoked membership reads INACTIVE even if its login was never claimed (MemberStatus alone would say INVITED).
		MemberStatus status = hasAccess ? MemberStatus.of(chosen.getUser().getStatus(), chosen.getStatus()) : MemberStatus.INACTIVE;
		return new StaffAccessSummary(hasAccess, chosen.getId(), chosen.getRole().getName(), status.name());
	}

	private AccessResult issueAccess(StaffProfile staffProfile, Role role, Long campusId, StaffAccessRequest access, Long actorId) {
		StaffInviteService.IssuedInvite invite = staffInviteService.issue(access.email().trim(), normalisedPhone(access));
		emailVerificationService.issue(invite.userId());
		OrganisationMembership membership = membershipService.grant(invite.userId(), staffProfile.getPerson().getId(), role.getId(),
				campusId, staffProfile.getDepartment() == null ? null : staffProfile.getDepartment().getId(), null,
				staffProfile.getDateOfJoining(), actorId);
		return new AccessResult(staffProfile, membership, invite);
	}

	private void requireMembershipManage(Long actorId) {
		permissionChecker.require(actorId, "MEMBERSHIP_MANAGE");
	}

	private void requireNoActiveAccess(Person person) {
		boolean hasAccess = membershipRepository.findByPersonId(person.getId()).stream()
				.anyMatch(membership -> membership.getStatus() == MembershipStatus.ACTIVE);
		if (hasAccess) {
			throw new IllegalStateException("This person already has login access");
		}
	}

	private void requireEmailFree(String email) {
		if (userRepository.findByEmail(email.trim()).isPresent()) {
			throw new IllegalStateException("A login with the email " + email.trim() + " already exists");
		}
	}

	private static String normalisedPhone(StaffAccessRequest access) {
		return access.phone() == null || access.phone().isBlank() ? null : access.phone().trim();
	}

	/** Phone is unique per organisation (uq_users_org_phone); say so up front rather than let the constraint surface as a
	 * generic conflict after the staff profile has been written. */
	private void requirePhoneFree(String phone) {
		if (phone != null && userRepository.existsByPhone(phone)) {
			throw new IllegalStateException("A login with the phone number " + phone + " already exists");
		}
	}

	/** The access block's campus, else the staff member's own; validated now so a bad id cannot surface after the invite email. */
	private Long resolveCampusId(StaffAccessRequest access, Campus staffCampus) {
		if (access.campusId() == null) {
			return staffCampus == null ? null : staffCampus.getId();
		}
		campusRepository.findById(access.campusId())
				.orElseThrow(() -> new IllegalArgumentException("No campus with id " + access.campusId()));
		return access.campusId();
	}

	public record AccessResult(StaffProfile staffProfile, OrganisationMembership membership, StaffInviteService.IssuedInvite invite) {
	}
}
