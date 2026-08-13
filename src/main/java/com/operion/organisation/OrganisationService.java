package com.operion.organisation;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.operion.audit.AuditLogService;
import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.authorization.Permission;
import com.operion.authorization.PermissionRepository;
import com.operion.authorization.Role;
import com.operion.authorization.RoleRepository;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns organisation provisioning (org + default campus + config + starter roles +
 * first admin login) and the only path by which an org's status changes.
 */
@Service
public class OrganisationService {

	private static final String DEFAULT_CAMPUS_NAME = "Main Campus";
	private static final String DEFAULT_CAMPUS_CODE = "MAIN";

	private final OrganisationRepository organisationRepository;
	private final CampusRepository campusRepository;
	private final OrganisationConfigurationRepository configurationRepository;
	private final RoleRepository roleRepository;
	private final PermissionRepository permissionRepository;
	private final UserRepository userRepository;
	private final PersonRepository personRepository;
	private final OrganisationMembershipRepository membershipRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuditLogService auditLogService;

	public OrganisationService(OrganisationRepository organisationRepository, CampusRepository campusRepository,
			OrganisationConfigurationRepository configurationRepository, RoleRepository roleRepository,
			PermissionRepository permissionRepository, UserRepository userRepository, PersonRepository personRepository,
			OrganisationMembershipRepository membershipRepository, PasswordEncoder passwordEncoder,
			AuditLogService auditLogService) {
		this.organisationRepository = organisationRepository;
		this.campusRepository = campusRepository;
		this.configurationRepository = configurationRepository;
		this.roleRepository = roleRepository;
		this.permissionRepository = permissionRepository;
		this.userRepository = userRepository;
		this.personRepository = personRepository;
		this.membershipRepository = membershipRepository;
		this.passwordEncoder = passwordEncoder;
		this.auditLogService = auditLogService;
	}

	// ponytail: not wrapped in one @Transactional - Hibernate resolves the tenant identifier
	// once per session/transaction (see ai-context/erp-system-plan.md §1.4's spike finding),
	// so a single enclosing transaction here would stamp Campus/Config/Role with whatever
	// tenant was current when the *first* call in the method opened the session - i.e. none,
	// since the organisation doesn't have an id yet at that point. Each save below gets its
	// own auto-transaction instead, opening a fresh session that picks up TenantContext
	// correctly. Trade-off: provisioning isn't atomic across campus/config/roles - if that
	// ever needs to be all-or-nothing, wrap phase two in its own @Transactional called through
	// a self-injected proxy (or move it behind a queue/compensation step).
	public Organisation provision(String name, String legalName, String slug, NewAdminAccount admin) {
		Organisation organisation = organisationRepository.save(new Organisation(name, legalName, slug));

		TenantContext.set(organisation.getId(), null);
		try {
			campusRepository.save(new Campus(DEFAULT_CAMPUS_NAME, DEFAULT_CAMPUS_CODE));
			configurationRepository.save(new OrganisationConfiguration(organisation));
			Role adminRole = seedDefaultRoles();
			createAdminMembership(admin, adminRole);
			auditLogService.record("Organisation", organisation.getId(), "CREATE", null, organisation.getStatus());
		} finally {
			TenantContext.clear();
		}

		return organisation;
	}

	private void createAdminMembership(NewAdminAccount admin, Role adminRole) {
		// A person who already has a User from another org (or a retried provisioning
		// attempt after a partial earlier failure - see the comment on provision() above)
		// is reused as-is rather than crashing on User.email's global unique constraint;
		// their existing password is never overwritten. Same pattern as
		// PortalInviteService.claim().
		User user = userRepository.findByEmail(admin.email())
				.orElseGet(() -> userRepository.save(new User(admin.email(), null, passwordEncoder.encode(admin.password()))));
		Person person = new Person(admin.firstName(), admin.lastName());
		person.setUser(user);
		person = personRepository.save(person);
		membershipRepository.save(new OrganisationMembership(user, person, adminRole, null));
	}

	@Transactional
	public Organisation changeStatus(Long organisationId, OrganisationStatus target) {
		Organisation organisation = organisationRepository.findById(organisationId)
				.orElseThrow(() -> new IllegalArgumentException("No organisation with id " + organisationId));

		OrganisationStatus previous = organisation.getStatus();
		organisation.changeStatus(target);
		auditLogService.record("Organisation", organisationId, "STATUS_CHANGE", previous, target);

		return organisation;
	}

	private Role seedDefaultRoles() {
		Map<String, Permission> permissionsByCode = permissionRepository.findAll().stream()
				.collect(Collectors.toMap(Permission::getCode, Function.identity()));

		Role admin = new Role(DefaultRoles.ORG_ADMIN, "Full access - system default, cannot be locked out", true);
		permissionsByCode.values().forEach(admin::grant);
		admin = roleRepository.save(admin);

		DefaultRoles.NON_ADMIN_ROLES.forEach((roleName, permissionCodes) -> {
			Role role = new Role(roleName, roleName + " (default)", false);
			permissionCodes.stream()
					.map(permissionsByCode::get)
					.filter(permission -> permission != null)
					.forEach(role::grant);
			roleRepository.save(role);
		});

		return admin;
	}
}
