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
 * One named filter a report's SQL binds as `:name` (see ReportExecutionService). No
 * owning-side collection on SavedReport - same FK-only convention as PurchaseOrderLine.
 */
@Getter
@Entity
@Table(name = "saved_report_parameters")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavedReportParameter extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "saved_report_id")
	private SavedReport savedReport;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReportParameterType type;

	@Column(nullable = false, length = 150)
	private String label;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	public SavedReportParameter(SavedReport savedReport, String name, ReportParameterType type, String label, int sortOrder) {
		this.savedReport = savedReport;
		this.name = name;
		this.type = type;
		this.label = label;
		this.sortOrder = sortOrder;
	}
}
