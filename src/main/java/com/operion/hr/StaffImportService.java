package com.operion.hr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.operion.common.imports.ImportFileParser;
import com.operion.common.imports.ImportRowResult;
import com.operion.common.imports.ImportRowStatus;
import com.operion.common.imports.ImportRunService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Parses the uploaded file and delegates each row to StaffRowImportService (own
 * transaction per row - see that class's javadoc). Deliberately not @Transactional
 * itself, same reason as StudentImportService: a partial import is the point, not an
 * all-or-nothing batch. validateOnly is passed straight through so a dry-run reuses the
 * exact same per-row resolution/duplicate-check logic as a real import.
 */
@Service
public class StaffImportService {

	private final StaffRowImportService staffRowImportService;
	private final ImportRunService importRunService;

	public StaffImportService(StaffRowImportService staffRowImportService, ImportRunService importRunService) {
		this.staffRowImportService = staffRowImportService;
		this.importRunService = importRunService;
	}

	public List<ImportRowResult> importFile(MultipartFile file, boolean validateOnly) {
		List<Map<String, String>> rows = ImportFileParser.parse(file);
		List<ImportRowResult> results = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++) {
			int rowNumber = i + 2; // header is row 1, first data row is row 2
			results.add(importRow(rowNumber, rows.get(i), validateOnly));
		}
		if (!validateOnly) {
			importRunService.record("Staff", file.getOriginalFilename(), results);
		}
		return results;
	}

	// StaffRowImportService.importRow throws (rather than catching internally) so its
	// REQUIRES_NEW transaction actually rolls back the row on failure - this is where
	// that exception becomes a reported failure instead of aborting the whole batch.
	private ImportRowResult importRow(int rowNumber, Map<String, String> row, boolean validateOnly) {
		try {
			return staffRowImportService.importRow(rowNumber, row, validateOnly);
		} catch (Exception e) {
			return new ImportRowResult(rowNumber, ImportRowStatus.ERROR, e.getMessage(), null);
		}
	}
}
