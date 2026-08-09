package com.operion.authorization;

import java.util.HashSet;
import java.util.Set;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A named, org-configurable permission bundle. RolePermission is a plain join table
 * with no attributes of its own, so it's modelled as a @ManyToMany rather than a
 * separate entity - per ai-context/erp-system-plan.md §1.5.
 */
@Getter
@Entity
@Table(name = "roles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role extends TenantScopedEntity {

	@Setter
	@Column(nullable = false)
	private String name;

	@Setter
	private String description;

	/** Protects a fallback admin role from being deleted/stripped into a lockout. */
	@Column(name = "is_system_default", nullable = false)
	private boolean systemDefault;

	@Setter
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RoleStatus status;

	@ManyToMany
	@JoinTable(name = "role_permissions",
			joinColumns = @JoinColumn(name = "role_id"),
			inverseJoinColumns = @JoinColumn(name = "permission_id"))
	private Set<Permission> permissions = new HashSet<>();

	public Role(String name, String description, boolean systemDefault) {
		this.name = name;
		this.description = description;
		this.systemDefault = systemDefault;
		this.status = RoleStatus.ACTIVE;
	}

	public void grant(Permission permission) {
		permissions.add(permission);
	}

	public void revoke(Permission permission) {
		if (systemDefault && permissions.size() <= 1) {
			throw new IllegalStateException("Cannot strip the last permission from a system-default role");
		}
		permissions.remove(permission);
	}
}
