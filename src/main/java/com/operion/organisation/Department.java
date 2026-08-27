package com.operion.organisation;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Org-defined structural catalog (Accounts, Admissions, Maintenance...) - part of
 * "Organisation Structure" alongside Campus, generic across industries (no
 * school-specific fields), replacing what used to be a free-text String on
 * StaffProfile. See ai-context/platform-boundaries.md.
 */
@Getter
@Entity
@Table(name = "departments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department extends TenantScopedEntity {

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DepartmentStatus status;

	public Department(String name) {
		this.name = name;
		this.status = DepartmentStatus.ACTIVE;
	}

	public void changeStatus(DepartmentStatus status) {
		this.status = status;
	}
}
