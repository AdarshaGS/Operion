package com.operion.authorization;

import com.operion.audit.AuditLogService;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
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
	}

	@Transactional
	public OrganisationMembership grant(Long userId, Long personId, Long roleId, Long campusId, Long departmentId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("No user with id " + userId));
		Person person = personRepository.findById(personId)
				.orElseThrow(() -> new IllegalArgumentException("No person with id " + personId));
		Role role = roleRepository.findById(roleId)
				.orElseThrow(() -> new IllegalArgumentException("No role with id " + roleId));
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

		OrganisationMembership membership = membershipRepository.save(new OrganisationMembership(user, person, role, campus, department));
		auditLogService.record("OrganisationMembership", membership.getId(), "GRANT", null, role.getName());
		return membership;
	}

	/** Takes an id and loads internally rather than accepting a controller-loaded
	 * membership - a detached entity's setter call never reaches the database on its own,
	 * it needs an explicit save() within this method's own transaction. Blocks revoking the
	 * org's last active system-default (Org Admin) membership - the same "cannot be locked
	 * out" invariant Role.revoke already protects at the permission level, applied here at
	 * the membership level. */
	@Transactional
	public OrganisationMembership revoke(Long membershipId) {
		OrganisationMembership membership = membershipRepository.findById(membershipId)
				.orElseThrow(() -> new IllegalArgumentException("No membership with id " + membershipId));

		if (membership.getRole().isSystemDefault()) {
			boolean anotherActiveAdmin = membershipRepository.findByStatus(MembershipStatus.ACTIVE).stream()
					.anyMatch(other -> !other.getId().equals(membership.getId()) && other.getRole().isSystemDefault());
			if (!anotherActiveAdmin) {
				throw new IllegalStateException("Cannot revoke the last active Org Admin membership");
			}
		}
		membership.setStatus(MembershipStatus.INACTIVE);
		OrganisationMembership saved = membershipRepository.save(membership);
		auditLogService.record("OrganisationMembership", saved.getId(), "REVOKE", MembershipStatus.ACTIVE, MembershipStatus.INACTIVE);
		return saved;
	}
}
