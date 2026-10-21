package com.operion.certificate;

import com.operion.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Shared, non-tenant starting wording for a certificate type, seeded by migration. The School setup
 * template copies one into a school's own {@link CertificateType} by code; after that the school's
 * copy is edited freely and this row is never consulted again for it.
 */
@Getter
@Entity
@Table(name = "certificate_type_presets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CertificateTypePreset extends BaseEntity {

	@Column(nullable = false, length = 30)
	private String code;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, length = 150)
	private String title;

	@Column(name = "body_template", nullable = false, columnDefinition = "TEXT")
	private String bodyTemplate;

	@Column(name = "particulars_template", columnDefinition = "TEXT")
	private String particularsTemplate;

	@Column(name = "manual_fields", columnDefinition = "TEXT")
	private String manualFields;

	@Column(name = "number_format", nullable = false, length = 60)
	private String numberFormat;

	@Column(name = "once_per_student", nullable = false)
	private boolean oncePerStudent;

	@Column(name = "requires_exit", nullable = false)
	private boolean requiresExit;

	@Column(name = "requires_clearance", nullable = false)
	private boolean requiresClearance;

	@Column(name = "approval_required", nullable = false)
	private boolean approvalRequired;

	@Column(name = "guardian_requestable", nullable = false)
	private boolean guardianRequestable;

	/** Test and seed use only - production presets come from the migration. */
	public CertificateTypePreset(String code, String name, String title, String bodyTemplate, String particularsTemplate, String manualFields,
			String numberFormat, boolean oncePerStudent, boolean requiresExit, boolean requiresClearance, boolean approvalRequired,
			boolean guardianRequestable) {
		this.code = code;
		this.name = name;
		this.title = title;
		this.bodyTemplate = bodyTemplate;
		this.particularsTemplate = particularsTemplate;
		this.manualFields = manualFields;
		this.numberFormat = numberFormat;
		this.oncePerStudent = oncePerStudent;
		this.requiresExit = requiresExit;
		this.requiresClearance = requiresClearance;
		this.approvalRequired = approvalRequired;
		this.guardianRequestable = guardianRequestable;
	}
}
