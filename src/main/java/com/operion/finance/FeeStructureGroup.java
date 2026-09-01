package com.operion.finance;

import com.operion.academic.SchoolClass;
import com.operion.common.TenantScopedEntity;
import com.operion.organisation.AcademicYear;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The named "one fee structure, several components" setup an admin configures once per
 * class per year (e.g. "Grade 5 Annual Fees 2026-27") - the individual FeeCategory
 * amounts live on the FeeStructure rows underneath it. Class-level, not section-level -
 * same convention as ClassSubject; all sections of a grade share one fee setup. Unique
 * (organisation_id, academic_year_id, school_class_id): one named setup per class per
 * year, not several competing ones. Per issue #129.
 */
@Getter
@Entity
@Table(name = "fee_structure_groups")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeeStructureGroup extends TenantScopedEntity {

	@Column(nullable = false)
	private String name;

	@ManyToOne(optional = false)
	@JoinColumn(name = "academic_year_id")
	private AcademicYear academicYear;

	@ManyToOne(optional = false)
	@JoinColumn(name = "school_class_id")
	private SchoolClass schoolClass;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private FeeStructureGroupStatus status;

	public FeeStructureGroup(String name, AcademicYear academicYear, SchoolClass schoolClass) {
		this.name = name;
		this.academicYear = academicYear;
		this.schoolClass = schoolClass;
		this.status = FeeStructureGroupStatus.ACTIVE;
	}
}
