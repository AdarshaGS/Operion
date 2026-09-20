package com.operion.common.imports;

/**
 * Shared per-row import result for every entity built after the original
 * student-only import (see com.operion.student.api.StudentImportRowResult, kept as-is
 * for backward compatibility). VALID/DUPLICATE only ever occur when the row was
 * processed with validateOnly=true; a real (validateOnly=false) run only ever reports
 * IMPORTED or ERROR.
 */
public record ImportRowResult(int row, ImportRowStatus status, String message, Long id) {
}
