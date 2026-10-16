package com.operion.authorization;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.operion.audit.AuditLogService;
import com.operion.common.InvalidRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {

	private final RoleRepository roleRepository;
	private final PermissionRepository permissionRepository;
	private final AuditLogService auditLogService;
	private final OrganisationMembershipRepository membershipRepository;

	public RoleService(RoleRepository roleRepository, PermissionRepository permissionRepository, AuditLogService auditLogService,
			OrganisationMembershipRepository membershipRepository) {
		this.roleRepository = roleRepository;
		this.permissionRepository = permissionRepository;
		this.auditLogService = auditLogService;
		this.membershipRepository = membershipRepository;
	}

	@Transactional
	public Role create(String name, String description, Set<String> permissionCodes) {
		return create(name, description, permissionCodes, false);
	}

	@Transactional
	public Role create(String name, String description, Set<String> permissionCodes, boolean portal) {
		if (name == null || name.isBlank()) {
			throw new InvalidRequestException("A role name is required");
		}
		if (permissionCodes == null) {
			permissionCodes = Set.of();
		}
		Role role = new Role(name.trim(), description, false, false, portal);
		resolvePermissions(permissionCodes).forEach(role::grant);
		role = roleRepository.save(role);
		auditLogService.record("Role", role.getId(), "CREATE", null, permissionCodes);
		return role;
	}

	@Transactional
	public Role updatePortalFlag(Long roleId, boolean portal) {
		return updatePortalFlag(roleId, portal, null);
	}

	/** {@code actorId} is the signed-in user on the HTTP path: they may not change a role they hold themselves.
	 * Checked after the role's own protections so the Owner role answers 409 whoever asks. */
	@Transactional
	public Role updatePortalFlag(Long roleId, boolean portal, Long actorId) {
		Role role = findOrThrow(roleId);
		if (portal && role.isSystemDefault()) {
			throw new IllegalStateException("The system-default admin role cannot be a portal role - its holders would be routed away from the admin screens");
		}
		rejectActorHolding(roleId, actorId);
		boolean before = role.isPortal();
		role.setPortal(portal);
		role = roleRepository.save(role);
		auditLogService.record("Role", role.getId(), "PORTAL_FLAG_CHANGE", before, portal);
		return role;
	}

	/** Takes an id and loads internally rather than accepting a controller-loaded Role -
	 * the role's own permission Set and the freshly-resolved target permissions must come
	 * from the same persistence context, or two Permission instances for the same DB row
	 * (loaded in different sessions) won't dedupe in a plain HashSet (Permission has no
	 * overridden equals/hashCode). Grants new codes before revoking dropped ones, so a
	 * same-size swap never trips {@link Role#revoke}'s system-default zero-permission guard. */
	@Transactional
	public Role updatePermissions(Long roleId, Set<String> permissionCodes) {
		return updatePermissions(roleId, permissionCodes, null);
	}

	@Transactional
	public Role updatePermissions(Long roleId, Set<String> permissionCodes, Long actorId) {
		if (permissionCodes == null) {
			throw new InvalidRequestException("permissionCodes is required");
		}
		Role role = findOrThrow(roleId);
		Set<String> before = role.getPermissions().stream().map(Permission::getCode).collect(Collectors.toSet());

		resolvePermissions(permissionCodes).forEach(role::grant);
		role.getPermissions().stream()
				.filter(permission -> !permissionCodes.contains(permission.getCode()))
				.toList()
				.forEach(role::revoke);
		rejectActorHolding(roleId, actorId);

		role = roleRepository.save(role);
		auditLogService.record("Role", role.getId(), "PERMISSIONS_UPDATE", before, permissionCodes);
		return role;
	}

	@Transactional
	public Role changeStatus(Long roleId, RoleStatus status) {
		return changeStatus(roleId, status, null);
	}

	@Transactional
	public Role changeStatus(Long roleId, RoleStatus status, Long actorId) {
		Role role = findOrThrow(roleId);
		if (role.isSystemDefault() && status == RoleStatus.INACTIVE) {
			throw new IllegalStateException("Cannot deactivate the system-default admin role");
		}
		if (role.isManaged() && status == RoleStatus.INACTIVE) {
			throw new IllegalStateException("Cannot deactivate a system-managed role");
		}
		rejectActorHolding(roleId, actorId);
		RoleStatus previous = role.getStatus();
		role.setStatus(status);
		role = roleRepository.save(role);
		auditLogService.record("Role", role.getId(), "STATUS_CHANGE", previous, status);
		return role;
	}

	/** Nobody may change what a role they themselves hold grants - another member with ROLE_MANAGE has to do it.
	 * Only the HTTP path passes an actor, so internal callers (org provisioning, the school template) can still seed roles. */
	private void rejectActorHolding(Long roleId, Long actorId) {
		if (actorId != null && membershipRepository.existsByUserIdAndRoleIdAndStatus(actorId, roleId, MembershipStatus.ACTIVE)) {
			throw new AuthorizationDeniedException("You cannot change a role you hold yourself - ask another administrator to do it");
		}
	}

	private Role findOrThrow(Long roleId) {
		return roleRepository.findById(roleId).orElseThrow(() -> new IllegalArgumentException("No role with id " + roleId));
	}

	private Set<Permission> resolvePermissions(Set<String> codes) {
		Map<String, Permission> byCode = permissionRepository.findAll().stream()
				.collect(Collectors.toMap(Permission::getCode, Function.identity()));
		return codes.stream()
				.map(code -> {
					Permission permission = byCode.get(code);
					if (permission == null) {
						throw new InvalidRequestException("No permission with code " + code);
					}
					return permission;
				})
				.collect(Collectors.toSet());
	}
}
