package com.operion.common.imports;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class ImportRunService {

	private final ImportRunRepository importRunRepository;

	public ImportRunService(ImportRunRepository importRunRepository) {
		this.importRunRepository = importRunRepository;
	}

	public void record(String entityType, String fileName, List<ImportRowResult> results) {
		int imported = 0;
		int errors = 0;
		int duplicates = 0;
		for (ImportRowResult result : results) {
			switch (result.status()) {
				case IMPORTED -> imported++;
				case DUPLICATE -> duplicates++;
				default -> errors++;
			}
		}
		importRunRepository.save(new ImportRun(entityType, fileName, results.size(), imported, errors, duplicates));
	}
}
