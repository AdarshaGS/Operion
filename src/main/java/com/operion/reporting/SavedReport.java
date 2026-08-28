package com.operion.reporting;

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
 * A user-authored SQL query plus its filter-parameter and column-display metadata (see
 * SavedReportParameter/SavedReportColumn). Ownership is answered by the inherited
 * created_by audit column, not a dedicated field - see V52's migration comment.
 * DRAFT -> PUBLISHED is one-way; ARCHIVED is terminal and blocks further edits, but is
 * reachable from either DRAFT or PUBLISHED (no need to publish something before retiring
 * a report nobody ended up using).
 */
@Getter
@Entity
@Table(name = "saved_reports")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavedReport extends TenantScopedEntity {

	@Column(nullable = false, length = 150)
	private String name;

	@Column(length = 500)
	private String description;

	@Column(name = "sql_query", nullable = false, columnDefinition = "TEXT")
	private String sqlQuery;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SavedReportStatus status;

	public SavedReport(String name, String description, String sqlQuery) {
		this.name = name;
		this.description = description;
		this.sqlQuery = sqlQuery;
		this.status = SavedReportStatus.DRAFT;
	}

	public void updateDefinition(String name, String description, String sqlQuery) {
		if (status == SavedReportStatus.ARCHIVED) {
			throw new IllegalStateException("Cannot edit an archived report");
		}
		this.name = name;
		this.description = description;
		this.sqlQuery = sqlQuery;
	}

	public void publish() {
		if (status != SavedReportStatus.DRAFT) {
			throw new IllegalStateException("Only a draft report can be published, was " + status);
		}
		this.status = SavedReportStatus.PUBLISHED;
	}

	public void archive() {
		if (status == SavedReportStatus.ARCHIVED) {
			throw new IllegalStateException("Report is already archived");
		}
		this.status = SavedReportStatus.ARCHIVED;
	}
}
