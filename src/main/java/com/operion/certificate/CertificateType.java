package com.operion.certificate;

import java.util.ArrayList;
import java.util.List;

import com.operion.common.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A kind of certificate this school issues, with its own wording. bodyTemplate is paragraphs of plain text
 * (blank line between them); particularsTemplate is optional "Label | value" lines printed as a table; both
 * take {{token}} merge fields (see CertificateMergeFields). manualFields is "key | Label" lines for values
 * staff type in per request, used as {{manual.key}}. The flags drive the request workflow.
 */
@Getter
@Entity
@Table(name = "certificate_types")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CertificateType extends TenantScopedEntity {

	public record ManualField(String key, String label) {
	}

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

	/** Next value of {SEQ}; only ever read and bumped under a row lock. */
	@Column(name = "next_sequence", nullable = false)
	private long nextSequence = 1;

	@Column(name = "page_size", nullable = false, length = 20)
	private String pageSize = "A4";

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

	@Column(nullable = false)
	private boolean active = true;

	public CertificateType(String code, String name) {
		this.code = code;
		this.name = name;
	}

	public static CertificateType fromPreset(CertificateTypePreset preset, String name) {
		CertificateType type = new CertificateType(preset.getCode(), name != null && !name.isBlank() ? name : preset.getName());
		type.update(type.name, preset.getTitle(), preset.getBodyTemplate(), preset.getParticularsTemplate(), preset.getManualFields(),
				preset.getNumberFormat(), "A4", preset.isOncePerStudent(), preset.isRequiresExit(), preset.isRequiresClearance(),
				preset.isApprovalRequired(), preset.isGuardianRequestable(), true);
		return type;
	}

	public void update(String name, String title, String bodyTemplate, String particularsTemplate, String manualFields, String numberFormat,
			String pageSize, boolean oncePerStudent, boolean requiresExit, boolean requiresClearance, boolean approvalRequired,
			boolean guardianRequestable, boolean active) {
		this.name = name;
		this.title = title;
		this.bodyTemplate = bodyTemplate;
		this.particularsTemplate = blankToNull(particularsTemplate);
		this.manualFields = blankToNull(manualFields);
		this.numberFormat = numberFormat;
		this.pageSize = pageSize;
		this.oncePerStudent = oncePerStudent;
		this.requiresExit = requiresExit;
		this.requiresClearance = requiresClearance;
		this.approvalRequired = approvalRequired;
		this.guardianRequestable = guardianRequestable;
		this.active = active;
	}

	/** Hands out the current sequence value and advances it. Caller must hold the row lock. */
	public long takeSequence() {
		return nextSequence++;
	}

	public List<ManualField> manualFieldList() {
		List<ManualField> fields = new ArrayList<>();
		if (manualFields == null) {
			return fields;
		}
		for (String line : manualFields.split("\\R")) {
			if (line.isBlank()) {
				continue;
			}
			int bar = line.indexOf('|');
			String key = (bar < 0 ? line : line.substring(0, bar)).trim();
			String label = bar < 0 ? key : line.substring(bar + 1).trim();
			if (!key.isEmpty()) {
				fields.add(new ManualField(key, label.isEmpty() ? key : label));
			}
		}
		return fields;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
