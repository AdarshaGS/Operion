package com.operion.common.imports;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Insert-only summary of one completed bulk import (see ImportRunService). */
@Getter
@Entity
@Table(name = "import_runs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImportRun extends TenantScopedEntity {

	@Column(name = "entity_type", nullable = false, length = 100)
	private String entityType;

	@Column(name = "file_name", nullable = false)
	private String fileName;

	@Column(name = "total_rows", nullable = false)
	private int totalRows;

	@Column(name = "imported_count", nullable = false)
	private int importedCount;

	@Column(name = "error_count", nullable = false)
	private int errorCount;

	@Column(name = "duplicate_count", nullable = false)
	private int duplicateCount;

	public ImportRun(String entityType, String fileName, int totalRows, int importedCount, int errorCount, int duplicateCount) {
		this.entityType = entityType;
		this.fileName = fileName;
		this.totalRows = totalRows;
		this.importedCount = importedCount;
		this.errorCount = errorCount;
		this.duplicateCount = duplicateCount;
	}
}
