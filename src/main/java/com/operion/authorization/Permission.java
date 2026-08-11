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

	/** Package-private, not exposed via any service/controller - the catalog is closed and
	 * only ever populated by a Flyway migration in real deployments. Exists so tests can
	 * build a fixture row directly instead of depending on migration data that intentionally
	 * doesn't run against the H2 test database (see application-test properties). */
	Permission(String code, String module, String description) {
		this.code = code;
		this.module = module;
		this.description = description;
	}
}
