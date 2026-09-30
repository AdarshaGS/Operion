package com.operion.platform.crm;

import com.operion.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A prospective school not yet onboarded as an Organisation. Platform-scoped (extends
 * BaseEntity, not TenantScopedEntity), like every other platform-plane entity - owner is a
 * PlatformAdmin id, kept as a plain column rather than a relationship since nothing here
 * needs the admin entity itself.
 */
@Getter
@Entity
@Table(name = "crm_leads")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Lead extends BaseEntity {

	@Column(name = "school_name", nullable = false)
	private String schoolName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LeadSource source;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LeadStage stage;

	@Column(name = "estimated_student_count")
	private Integer estimatedStudentCount;

	@Column(name = "owner_id")
	private Long ownerId;

	@Column(columnDefinition = "TEXT")
	private String notes;

	/** Set once, when the lead is converted into an Organisation. */
	@Column(name = "converted_organisation_id")
	private Long convertedOrganisationId;

	public Lead(String schoolName, LeadSource source, Integer estimatedStudentCount, Long ownerId, String notes) {
		this.schoolName = schoolName;
		this.source = source;
		this.stage = LeadStage.NEW;
		this.estimatedStudentCount = estimatedStudentCount;
		this.ownerId = ownerId;
		this.notes = notes;
	}

	public void update(String schoolName, LeadSource source, Integer estimatedStudentCount, Long ownerId, String notes) {
		this.schoolName = schoolName;
		this.source = source;
		this.estimatedStudentCount = estimatedStudentCount;
		this.ownerId = ownerId;
		this.notes = notes;
	}

	public void changeStage(LeadStage target) {
		this.stage = target;
	}

	public boolean isConverted() {
		return convertedOrganisationId != null;
	}

	public void markConverted(Long organisationId) {
		this.convertedOrganisationId = organisationId;
	}
}
