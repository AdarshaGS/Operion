package com.operion.authorization;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.operion.audit.AuditLogService;
import com.operion.common.InvalidRequestException;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import com.operion.identity.UserStatus;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Department;
import com.operion.organisation.DepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService {

	private final OrganisationMembershipRepository membershipRepository;
	private final UserRepository userRepository;
	private final PersonRepository personRepository;
	private final RoleRepository roleRepository;
	private final CampusRepository campusRepository;
	private final DepartmentRepository departmentRepository;
	private final AuditLogService auditLogService;
	private final PermissionChecker permissionChecker;

	public MembershipService(OrganisationMembershipRepository membershipRepository, UserRepository userRepository,
			PersonRepository personRepository, RoleRepository roleRepository, CampusRepository campusRepository,
			DepartmentRepository departmentRepository, AuditLogService auditLogService) {
		this.membershipRepository = membershipRepository;
		this.userRepository = userRepository;
		this.personRepository = personRepository;
		this.roleRepository = roleRepository;
		this.campusRepository = campusRepository;
		this.departmentRepository = departmentRepository;
		this.auditLogService = auditLogService;
		this.permissionChecker = new PermissionChecker(membershipRepository);
	}

	public OrganisationMembership grant(Long userId, Long personId, Long roleId, Long campusId, Long departmentId) {
		return grant(userId, personId, roleId, campusId, departmentId, null, null);
	}

	@Transactional
	public OrganisationMembership grant(Long userId, Long personId, Long roleId, Long campusId, Long departmentId, String memberId,
			LocalDate joiningDate) {
		return grant(userId, personId, roleId, campusId, departmentId, memberId, joiningDate, null);
	}

	/** {@code actorId} is the signed-in user on the HTTP path: they may only grant a role whose permissions they already hold,
	 * so MEMBERSHIP_MANAGE alone can't be turned into admin access. Internal callers (portal invite claim) pass null. */
	@Transactional
	public OrganisationMembership grant(Long userId, Long personId, Long roleId, Long campusId, Long departmentId, String memberId,
			LocalDate joiningDate, Long actorId) {
		if (userId == null || personId == null || roleId == null) {
			throw new InvalidRequestException("userId, personId and roleId are required");
		}
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("No user with id " + userId));
		Person person = personRepository.findById(personId)
				.orElseThrow(() -> new IllegalArgumentException("No person with id " + personId));
		Role role = requireGrantableRole(roleId, actorId);
		Campus campus = campusId == null ? null : campusRepository.findById(campusId)
				.orElseThrow(() -> new IllegalArgumentException("No campus with id " + campusId));
		Department department = departmentId == null ? null : departmentRepository.findById(departmentId)
				.orElseThrow(() -> new IllegalArgumentException("No department with id " + departmentId));

		boolean alreadyActive = membershipRepository.findByUserId(userId).stream()
				.anyMatch(membership -> membership.getStatus() == MembershipStatus.ACTIVE
						&& membership.getRole().getId().equals(roleId));
		if (alreadyActive) {
			throw new IllegalStateException("User already holds an active membership with this role");
		}

		OrganisationMembership membership = new OrganisationMembership(user, person, role, campus, department);
		membership.setMemberId(memberId);
		membership.setJoiningDate(joiningDate);
		membership = membershipRepository.save(membership);
		auditLogService.record("OrganisationMembership", membership.getId(), "GRANT", null, role.getName());
		return membership;
	}

	/** The role rules every grant must pass, callable on their own so a caller that does other work first (e.g. issuing a
	 * login) can refuse an ineligible role up front instead of discovering it half way through: the role exists, is active,
	 * and - when {@code actorId} is given - the actor already holds everything the role grants. */
	public Role requireGrantableRole(Long roleId, Long actorId) {
		Role role = roleRepository.findById(roleId)
				.orElseThrow(() -> new IllegalArgumentException("No role with id " + roleId));
		if (role.getStatus() != RoleStatus.ACTIVE) {
			throw new IllegalStateException("The role " + role.getName() + " is inactive and cannot be assigned");
		}
		if (actorId != null) {
			List<String> roleCodes = role.getPermissions().stream().map(Permission::getCode).toList();
			permissionChecker.requireAll(actorId, roleCodes,
					"You can only assign a role whose permissions you hold yourself - ask an administrator to assign " + role.getName());
		}
		return role;
	}

	/**
	 * The refusals revoke() applies, callable beforehand and for a set of memberships revoked together (one person can hold
	 * several), so a caller can refuse before it writes anything. Nothing is written here. The Owner's membership is never
	 * revocable; a system-default (Org Admin) membership needs ANOTHER active admin outside the set whose login is itself
	 * ACTIVE - a PENDING (never claimed) or DISABLED user cannot administer anything, so counting one would strand the org.
	 */
	public void requireRevocable(Collection<OrganisationMembership> memberships) {
		if (memberships.stream().anyMatch(OrganisationMembership::isOwner)) {
			throw new IllegalStateException("Cannot revoke the organisation Owner's membership");
		}
		if (memberships.stream().anyMatch(membership -> membership.getRole().isSystemDefault())) {
			Set<Long> revoking = memberships.stream().map(OrganisationMembership::getId).collect(Collectors.toSet());
			boolean anotherActiveAdmin = membershipRepository.findByStatus(MembershipStatus.ACTIVE).stream()
					.anyMatch(other -> !revoking.contains(other.getId()) && other.getRole().isSystemDefault()
							&& other.getUser().getStatus() == UserStatus.ACTIVE);
			if (!anotherActiveAdmin) {
				throw new IllegalStateException("Cannot revoke the last active Org Admin membership");
			}
		}
	}

	/** Takes an id and loads internally rather than accepting a controller-loaded
	 * membership - a detached entity's setter call never reaches the database on its own,
	 * it needs an explicit save() within this method's own transaction. Blocks revoking the
	 * Owner's membership outright - ownership transfer isn't supported yet, so there is no
	 * way to designate a replacement Owner, and letting anyone (even another admin-tier
	 * holder) revoke it would strand the org with no Owner at all. Also keeps the older
	 * "last active system-default role" guard for any other systemDefault-role holder,
	 * the same "cannot be locked out" invariant Role.revoke already protects at the
	 * permission level, applied here at the membership level. */
	@Transactional
	public OrganisationMembership revoke(Long membershipId) {
		OrganisationMembership membership = membershipRepository.findById(membershipId)
				.orElseThrow(() -> new IllegalArgumentException("No membership with id " + membershipId));

		requireRevocable(List.of(membership));
		membership.setStatus(MembershipStatus.INACTIVE);
		OrganisationMembership saved = membershipRepository.save(membership);
		auditLogService.record("OrganisationMembership", saved.getId(), "REVOKE", MembershipStatus.ACTIVE, MembershipStatus.INACTIVE);
		return saved;
	}
}
