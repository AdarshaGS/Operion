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
 * Org-defined structural catalog (Principal, Accountant, Librarian...) - part of
 * "Organisation Structure" alongside Campus/Department, generic across industries (no
 * school-specific fields), replacing what used to be a free-text String on
 * StaffProfile. See ai-context/platform-boundaries.md.
 */
@Getter
@Entity
@Table(name = "designations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Designation extends TenantScopedEntity {

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DesignationStatus status;

	public Designation(String name) {
		this.name = name;
		this.status = DesignationStatus.ACTIVE;
	}

	public void changeStatus(DesignationStatus status) {
		this.status = status;
	}
}
