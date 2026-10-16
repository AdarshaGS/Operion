package com.operion.student;

import com.operion.common.imports.ImportFileParser;
import com.operion.common.imports.ImportRowErrors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.operion.student.api.StudentImportRowResult;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Parses the uploaded file (.csv or .xlsx) and delegates each row to
 * StudentRowImportService (own transaction per row - see that class's javadoc).
 * Deliberately not @Transactional itself: a partial import (some rows created, some
 * reported as failed) is the whole point (#28), not an all-or-nothing batch. .xlsx
 * support (#287) reads via Apache POI into the same header-row/data-rows shape the CSV
 * path already produces, so StudentRowImportService.importRow never needs to know which
 * format the upload came in.
 */
@Service
public class StudentImportService {

	/** Mirrors STUDENT_IMPORT_TEMPLATE_HEADERS in web/src/api/students.ts and the fields
	 * StudentRowImportService.importRow actually reads (#287) - kept here rather than
	 * generalized into the shared ImportFileParser/ImportRowResult classes per the
	 * decision to leave this importer's own parsing untouched. */
	private static final Set<String> KNOWN_HEADERS = Set.of("firstName", "lastName", "dateOfBirth", "gender", "email", "phone",
			"admissionNumber", "admissionDate", "admissionSource", "previousSchool", "tcNumber", "entranceScore", "bloodGroup",
			"category", "nationality", "remarks");

	private static final Set<String> REQUIRED_HEADERS = Set.of("firstName", "admissionDate");

	private final StudentRowImportService rowImportService;

	public StudentImportService(StudentRowImportService rowImportService) {
		this.rowImportService = rowImportService;
	}

	public List<StudentImportRowResult> importFile(MultipartFile file) {
		return importRows(ImportFileParser.parseRows(file));
	}

	/** Validates headers once, up front, rather than letting every data row fail with
	 * the same generic "X is required" message when the real problem is a typo'd or
	 * missing column (#287). Reported as a single row-1 result so it renders through
	 * the same per-row result list the import dialog already displays. */
	private List<StudentImportRowResult> importRows(List<List<String>> rows) {
		List<StudentImportRowResult> results = new ArrayList<>();
		if (rows.isEmpty()) {
			return results;
		}
		List<String> headers = rows.get(0);
		String headerError = validateHeaders(headers);
		if (headerError != null) {
			results.add(new StudentImportRowResult(1, false, headerError, null));
			return results;
		}
		for (int i = 1; i < rows.size(); i++) {
			List<String> values = rows.get(i);
			if (values.stream().allMatch(String::isBlank)) {
				continue;
			}
			int rowNumber = i + 1;
			results.add(importRow(rowNumber, headers, values));
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
			return new StudentImportRowResult(rowNumber, false, ImportRowErrors.message(e), null);
		}
	}

	private static Map<String, String> toRow(List<String> headers, List<String> values) {
		Map<String, String> row = new HashMap<>();
		for (int i = 0; i < headers.size() && i < values.size(); i++) {
			row.put(headers.get(i).trim(), values.get(i));
		}
		return row;
	}

	private static String validateHeaders(List<String> headers) {
		Set<String> trimmedHeaders = headers.stream().map(String::trim).collect(Collectors.toCollection(LinkedHashSet::new));

		Set<String> unrecognized = trimmedHeaders.stream().filter(h -> !KNOWN_HEADERS.contains(h)).collect(Collectors.toCollection(LinkedHashSet::new));
		Set<String> missingRequired = REQUIRED_HEADERS.stream().filter(h -> !trimmedHeaders.contains(h)).collect(Collectors.toCollection(LinkedHashSet::new));

		if (missingRequired.isEmpty() && unrecognized.isEmpty()) {
			return null;
		}
		StringBuilder message = new StringBuilder("Column mismatch - ");
		if (!missingRequired.isEmpty()) {
			message.append("missing required column(s): ").append(String.join(", ", missingRequired));
		}
		if (!unrecognized.isEmpty()) {
			if (!missingRequired.isEmpty()) {
				message.append("; ");
			}
			message.append("unrecognized column(s): ").append(String.join(", ", unrecognized));
		}
		message.append(". Expected columns: ").append(String.join(", ", KNOWN_HEADERS));
		return message.toString();
	}
}
