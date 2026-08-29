package com.operion.student;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.operion.common.CsvUtil;
import com.operion.student.api.StudentImportRowResult;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Parses the uploaded CSV and delegates each row to StudentRowImportService (own
 * transaction per row - see that class's javadoc). Deliberately not @Transactional
 * itself: a partial import (some rows created, some reported as failed) is the whole
 * point (#28), not an all-or-nothing batch.
 */
@Service
public class StudentImportService {

	private final StudentRowImportService rowImportService;

	public StudentImportService(StudentRowImportService rowImportService) {
		this.rowImportService = rowImportService;
	}

	public List<StudentImportRowResult> importCsv(MultipartFile file) {
		List<StudentImportRowResult> results = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
			String headerLine = reader.readLine();
			if (headerLine == null) {
				return results;
			}
			List<String> headers = CsvUtil.parseLine(headerLine);

			String line;
			int rowNumber = 1;
			while ((line = reader.readLine()) != null) {
				rowNumber++;
				if (line.isBlank()) {
					continue;
				}
				results.add(importRow(rowNumber, headers, CsvUtil.parseLine(line)));
			}
		} catch (IOException e) {
			throw new IllegalArgumentException("Could not read uploaded file: " + e.getMessage(), e);
		}
		return results;
	}

	// StudentRowImportService.importRow throws (rather than catching internally) so its
	// REQUIRES_NEW transaction actually rolls back the row on failure - this is where
	// that exception becomes a reported failure instead of aborting the whole batch.
	private StudentImportRowResult importRow(int rowNumber, List<String> headers, List<String> values) {
		try {
			return rowImportService.importRow(rowNumber, toRow(headers, values));
		} catch (Exception e) {
			return new StudentImportRowResult(rowNumber, false, e.getMessage(), null);
		}
	}

	private static Map<String, String> toRow(List<String> headers, List<String> values) {
		Map<String, String> row = new HashMap<>();
		for (int i = 0; i < headers.size() && i < values.size(); i++) {
			row.put(headers.get(i).trim(), values.get(i));
		}
		return row;
	}
}
