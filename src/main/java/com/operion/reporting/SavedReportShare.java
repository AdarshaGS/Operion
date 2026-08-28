package com.operion.reporting;

import com.operion.common.TenantScopedEntity;
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
 * Grants a specific user or role run/edit access to one report, on top of the coarse
 * REPORT_MANAGE catalog permission (which already sees/edits everything). principalId is
 * polymorphic (a User.id or Role.id per principalType) with no FK, same shape as
 * AuditLog's entityType/entityId. canEdit implies canRun; there's no standalone
 * "view but can't run" grant - a report has nothing to view except its own result.
 */
@Getter
@Entity
@Table(name = "saved_report_shares")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavedReportShare extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "saved_report_id")
	private SavedReport savedReport;

	@Enumerated(EnumType.STRING)
	@Column(name = "principal_type", nullable = false, length = 20)
	private SharePrincipalType principalType;

	@Column(name = "principal_id", nullable = false)
	private Long principalId;

	@Column(name = "can_run", nullable = false)
	private boolean canRun;

	@Column(name = "can_edit", nullable = false)
	private boolean canEdit;

	public SavedReportShare(SavedReport savedReport, SharePrincipalType principalType, Long principalId, boolean canRun, boolean canEdit) {
		this.savedReport = savedReport;
		this.principalType = principalType;
		this.principalId = principalId;
		this.canRun = canRun || canEdit;
		this.canEdit = canEdit;
	}
}
