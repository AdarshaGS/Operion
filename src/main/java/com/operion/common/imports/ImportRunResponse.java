package com.operion.common.imports;

import java.time.Instant;

public record ImportRunResponse(long id, String entityType, String fileName, int totalRows, int importedCount, int errorCount,
		int duplicateCount, Instant createdAt) {

	public static ImportRunResponse from(ImportRun run) {
		return new ImportRunResponse(run.getId(), run.getEntityType(), run.getFileName(), run.getTotalRows(), run.getImportedCount(),
				run.getErrorCount(), run.getDuplicateCount(), run.getCreatedAt());
	}
}
