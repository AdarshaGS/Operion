package com.operion.authorization;

import com.operion.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The fixed capability catalog (STUDENT_VIEW, FEE_COLLECT, ...). Global, seeded via
 * Flyway migration - tenants configure which permissions a role has (RolePermission),
 * never what permissions exist. That boundary is what keeps RBAC from becoming EAV.
 */
@Getter
@Entity
@Table(name = "permissions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Permission extends BaseEntity {

	@Column(nullable = false, unique = true)
	private String code;

	@Column(nullable = false)
	private String module;

	private String description;

	/** Not exposed via any service/controller anywhere - the catalog stays closed and only
	 * ever populated by a Flyway migration in real deployments regardless of this
	 * constructor's visibility. Public (rather than package-private) so tests in any module
	 * can build a fixture row directly instead of depending on migration data that
	 * intentionally doesn't run against the H2 test database (see application-test
	 * properties) - needed cross-package once more than one module's tests started
	 * exercising real permission codes (e.g. PortalInviteLifecycleTest). */
	public Permission(String code, String module, String description) {
		this.code = code;
		this.module = module;
		this.description = description;
	}
}
