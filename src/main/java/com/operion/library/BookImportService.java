package com.operion.library;

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
 * Parses the uploaded file and delegates each row to BookRowImportService (own
 * transaction per row). Deliberately not @Transactional itself - a partial import is the
 * point, not an all-or-nothing batch (see StudentImportService).
 */
@Service
public class BookImportService {

	private final BookRowImportService rowImportService;
	private final ImportRunService importRunService;

	public BookImportService(BookRowImportService rowImportService, ImportRunService importRunService) {
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
			importRunService.record("Book", file.getOriginalFilename(), results);
		}
		return results;
	}

	private ImportRowResult importRow(int rowNumber, Map<String, String> row, boolean validateOnly) {
		try {
			return rowImportService.importRow(rowNumber, row, validateOnly);
		} catch (Exception e) {
			return new ImportRowResult(rowNumber, ImportRowStatus.ERROR, e.getMessage(), null);
		}
	}
}
