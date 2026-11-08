package com.operion.student;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Org-wide admission policy: whether enrolling a student assigns and invoices the admission fee, and
 * (in a later phase) whether a student with that fee unpaid is blocked from academic writes. Lazy, at
 * most one row per organisation - same "no row until first PUT, defaults on GET" convention as
 * LibrarySettings and ExaminationSettings. Lives in the student package so a future gate needs no
 * finance dependency.
 */
@Getter
@Setter
@Entity
@Table(name = "admission_settings")
public class AdmissionSettings extends TenantScopedEntity {

	public static final AdmissionFeeClearanceMode DEFAULT_FEE_CLEARANCE_MODE = AdmissionFeeClearanceMode.OFF;

	@Enumerated(EnumType.STRING)
	@Column(name = "fee_clearance_mode", nullable = false, length = 20)
	private AdmissionFeeClearanceMode feeClearanceMode;

	public AdmissionSettings() {
		this.feeClearanceMode = DEFAULT_FEE_CLEARANCE_MODE;
	}
}
