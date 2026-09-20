package com.operion.finance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.operion.common.imports.ImportFileParser;
import com.operion.common.imports.ImportRowResult;
import com.operion.common.imports.ImportRowStatus;
import com.operion.common.imports.ImportRunService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OpeningBalanceImportService {

	private final OpeningBalanceRowImportService rowImportService;
	private final ImportRunService importRunService;

	public OpeningBalanceImportService(OpeningBalanceRowImportService rowImportService, ImportRunService importRunService) {
		this.rowImportService = rowImportService;
		this.importRunService = importRunService;
	}

	public List<ImportRowResult> importFile(MultipartFile file, boolean validateOnly) {
		List<Map<String, String>> rows = ImportFileParser.parse(file);
		List<ImportRowResult> results = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++) {
			int rowNumber = i + 2;
			try {
				results.add(rowImportService.importRow(rowNumber, rows.get(i), validateOnly));
			} catch (Exception e) {
				results.add(new ImportRowResult(rowNumber, ImportRowStatus.ERROR, e.getMessage(), null));
			}
		}
		if (!validateOnly) {
			importRunService.record("OpeningBalance", file.getOriginalFilename(), results);
		}
		return results;
	}
}
