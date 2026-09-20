package com.operion.parent;

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
 * Parses the uploaded CSV/Excel and delegates each row to GuardianRowImportService (own
 * transaction per row - see that class's javadoc). Deliberately not @Transactional
 * itself: a partial import is the point, same as StudentImportService.
 */
@Service
public class GuardianImportService {

	private final GuardianRowImportService rowImportService;
	private final ImportRunService importRunService;

	public GuardianImportService(GuardianRowImportService rowImportService, ImportRunService importRunService) {
		this.rowImportService = rowImportService;
		this.importRunService = importRunService;
	}

	public List<ImportRowResult> importFile(MultipartFile file, boolean validateOnly) {
		List<Map<String, String>> rows = ImportFileParser.parse(file);
		List<ImportRowResult> results = new ArrayList<>();
		int rowNumber = 1;
		for (Map<String, String> row : rows) {
			rowNumber++;
			results.add(importRow(rowNumber, row, validateOnly));
		}
		if (!validateOnly) {
			importRunService.record("Guardian", file.getOriginalFilename(), results);
		}
		return results;
	}

	// GuardianRowImportService.importRow throws (rather than catching internally) so its
	// REQUIRES_NEW transaction actually rolls back the row on failure - this is where
	// that exception becomes a reported failure instead of aborting the whole batch.
	private ImportRowResult importRow(int rowNumber, Map<String, String> row, boolean validateOnly) {
		try {
			return rowImportService.importRow(rowNumber, row, validateOnly);
		} catch (Exception e) {
			return new ImportRowResult(rowNumber, ImportRowStatus.ERROR, e.getMessage(), null);
		}
	}
}
