package com.operion.reporting;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Maps a raw result-set column name to a display label and order for the report UI. */
@Getter
@Entity
@Table(name = "saved_report_columns")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavedReportColumn extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "saved_report_id")
	private SavedReport savedReport;

	@Column(name = "source_column", nullable = false, length = 100)
	private String sourceColumn;

	@Column(nullable = false, length = 150)
	private String label;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	public SavedReportColumn(SavedReport savedReport, String sourceColumn, String label, int sortOrder) {
		this.savedReport = savedReport;
		this.sourceColumn = sourceColumn;
		this.label = label;
		this.sortOrder = sortOrder;
	}
}
