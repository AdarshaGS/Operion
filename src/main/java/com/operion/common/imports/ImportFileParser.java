package com.operion.common.imports;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.operion.common.CsvUtil;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

/**
 * Shared .csv/.xlsx reader for every importer built after the original student-only one
 * (see com.operion.student.StudentImportService, left untouched, whose own parsing this
 * mirrors). Returns header-keyed rows, blank rows skipped, so each entity's
 * XxxRowImportService never has to know which file format the upload came in.
 */
public final class ImportFileParser {

	private ImportFileParser() {
	}

	public static List<Map<String, String>> parse(MultipartFile file) {
		return toRowMaps(parseRows(file));
	}

	/** Raw rows (header row first) from a .csv or .xlsx upload. Every problem with the file
	 * itself surfaces as {@link ImportFileException} (400), never as a 404/500. */
	public static List<List<String>> parseRows(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ImportFileException("No file uploaded - attach a .csv or .xlsx file");
		}
		String filename = file.getOriginalFilename();
		String lowerName = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
		try {
			if (lowerName.endsWith(".xlsx")) {
				return readXlsxRows(file);
			}
			if (lowerName.endsWith(".csv") || filename == null) {
				return readCsvRows(file);
			}
		} catch (IOException e) {
			throw new ImportFileException("Could not read uploaded file - is it a valid " + (lowerName.endsWith(".xlsx") ? ".xlsx workbook" : ".csv file") + "?", e);
		} catch (RuntimeException e) {
			// POI throws unchecked exceptions for a file that merely carries an .xlsx name.
			throw new ImportFileException("Could not read uploaded file - is it a valid .xlsx workbook?", e);
		}
		throw new ImportFileException("Unsupported file type '" + filename + "' - upload a .csv or .xlsx file");
	}

	private static List<Map<String, String>> toRowMaps(List<List<String>> rows) {
		List<Map<String, String>> result = new ArrayList<>();
		if (rows.isEmpty()) {
			return result;
		}
		List<String> headers = rows.get(0);
		for (int i = 1; i < rows.size(); i++) {
			List<String> values = rows.get(i);
			if (values.stream().allMatch(String::isBlank)) {
				continue;
			}
			Map<String, String> row = new LinkedHashMap<>();
			for (int col = 0; col < headers.size() && col < values.size(); col++) {
				row.put(headers.get(col).trim(), values.get(col));
			}
			result.add(row);
		}
		return result;
	}

	private static List<List<String>> readCsvRows(MultipartFile file) throws IOException {
		List<List<String>> rows = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (rows.isEmpty() && line.startsWith("\uFEFF")) {
					// Excel's "CSV UTF-8" prepends a byte-order mark that would otherwise stick to the first header.
					line = line.substring(1);
				}
				if (!line.isBlank() || !rows.isEmpty()) {
					rows.add(CsvUtil.parseLine(line));
				}
			}
		}
		return rows;
	}

	private static List<List<String>> readXlsxRows(MultipartFile file) throws IOException {
		List<List<String>> rows = new ArrayList<>();
		DataFormatter formatter = new DataFormatter();
		try (XSSFWorkbook workbook = new XSSFWorkbook(file.getInputStream())) {
			Sheet sheet = workbook.getSheetAt(0);
			for (Row row : sheet) {
				List<String> values = new ArrayList<>();
				for (int col = 0; col < row.getLastCellNum(); col++) {
					var cell = row.getCell(col);
					values.add(cell == null ? "" : formatter.formatCellValue(cell));
				}
				rows.add(values);
			}
		}
		return rows;
	}
}
