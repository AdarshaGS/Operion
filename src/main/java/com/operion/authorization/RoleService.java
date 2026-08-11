package com.operion.authorization;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.operion.audit.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {

	private final RoleRepository roleRepository;
	private final PermissionRepository permissionRepository;
	private final AuditLogService auditLogService;

	public RoleService(RoleRepository roleRepository, PermissionRepository permissionRepository, AuditLogService auditLogService) {
		this.roleRepository = roleRepository;
		this.permissionRepository = permissionRepository;
		this.auditLogService = auditLogService;
	}

	@Transactional
	public Role create(String name, String description, Set<String> permissionCodes) {
		Role role = new Role(name, description, false);
		resolvePermissions(permissionCodes).forEach(role::grant);
		role = roleRepository.save(role);
		auditLogService.record("Role", role.getId(), "CREATE", null, permissionCodes);
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
		Role role = findOrThrow(roleId);
		Set<String> before = role.getPermissions().stream().map(Permission::getCode).collect(Collectors.toSet());

		resolvePermissions(permissionCodes).forEach(role::grant);
		role.getPermissions().stream()
				.filter(permission -> !permissionCodes.contains(permission.getCode()))
				.toList()
				.forEach(role::revoke);

		role = roleRepository.save(role);
		auditLogService.record("Role", role.getId(), "PERMISSIONS_UPDATE", before, permissionCodes);
		return role;
	}

	@Transactional
	public Role changeStatus(Long roleId, RoleStatus status) {
		Role role = findOrThrow(roleId);
		if (role.isSystemDefault() && status == RoleStatus.INACTIVE) {
			throw new IllegalStateException("Cannot deactivate the system-default admin role");
		}
		RoleStatus previous = role.getStatus();
		role.setStatus(status);
		role = roleRepository.save(role);
		auditLogService.record("Role", role.getId(), "STATUS_CHANGE", previous, status);
		return role;
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
						throw new IllegalArgumentException("No permission with code " + code);
					}
					return permission;
				})
				.collect(Collectors.toSet());
	}
}
