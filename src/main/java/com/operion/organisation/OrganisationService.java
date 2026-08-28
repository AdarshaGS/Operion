package com.operion.organisation;

import com.operion.audit.AuditLogService;
import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
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
	private final OrganisationBrandingRepository brandingRepository;
	private final RoleRepository roleRepository;
	private final PermissionRepository permissionRepository;
	private final UserRepository userRepository;
	private final PersonRepository personRepository;
	private final OrganisationMembershipRepository membershipRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuditLogService auditLogService;

	public OrganisationService(OrganisationRepository organisationRepository, CampusRepository campusRepository,
			OrganisationConfigurationRepository configurationRepository, OrganisationBrandingRepository brandingRepository,
			RoleRepository roleRepository, PermissionRepository permissionRepository, UserRepository userRepository,
			PersonRepository personRepository, OrganisationMembershipRepository membershipRepository,
			PasswordEncoder passwordEncoder, AuditLogService auditLogService) {
		this.organisationRepository = organisationRepository;
		this.campusRepository = campusRepository;
		this.configurationRepository = configurationRepository;
		this.brandingRepository = brandingRepository;
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
			brandingRepository.save(new OrganisationBranding(organisation));
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
		membershipRepository.save(new OrganisationMembership(user, person, adminRole, null, null, true));
	}

	/**
	 * Called from both the tenant plane (OrganisationController, where TenantContext
	 * already carries this organisationId) and the platform plane (PlatformOrganisationController,
	 * where PlatformAuthenticationInterceptor sets TenantContext.set(null, platformAdminId) -
	 * no tenant to inherit). AuditLogService.record() stamps organisation_id from
	 * TenantContext, so without this, a platform-triggered status change writes its audit
	 * row with a null organisation_id - saved correctly, but unattributed and unfindable via
	 * AuditLogController's findByOrganisationId(). Same save/restore-around-the-call pattern
	 * as BillingService.countActiveStudents() - a no-op on the tenant plane, the actual fix
	 * on the platform plane.
	 */
	@Transactional
	public Organisation changeStatus(Long organisationId, OrganisationStatus target) {
		Organisation organisation = organisationRepository.findById(organisationId)
				.orElseThrow(() -> new IllegalArgumentException("No organisation with id " + organisationId));

		OrganisationStatus previous = organisation.getStatus();
		organisation.changeStatus(target);

		Long previousOrganisationId = TenantContext.getOrganisationId();
		Long previousActorId = TenantContext.getActorId();
		try {
			TenantContext.set(organisationId, previousActorId);
			auditLogService.record("Organisation", organisationId, "STATUS_CHANGE", previous, target);
		} finally {
			TenantContext.set(previousOrganisationId, previousActorId);
		}

		return organisation;
	}

	// No industry-specific roles (Teacher/Accountant/...) seeded here - a fresh org gets
	// only its Owner, an otherwise-empty workspace, and creates its own roles afterwards via
	// the already-fully-generic RoleController (see GitHub #92).
	private Role seedDefaultRoles() {
		Role admin = new Role(DefaultRoles.OWNER, "Full access - system default, cannot be locked out", true);
		permissionRepository.findAll().forEach(admin::grant);
		return roleRepository.save(admin);
	}
}
