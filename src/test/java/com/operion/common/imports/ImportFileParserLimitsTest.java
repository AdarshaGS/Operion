package com.operion.common.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** Every bulk import reads its upload through ImportFileParser, so these limits cover all of them. */
class ImportFileParserLimitsTest {

	@AfterEach
	void restoreDefaults() {
		ImportFileParser.configure(5L * 1024 * 1024, 10_000, 200, 30L * 1024 * 1024, 0.01);
	}

	private static MockMultipartFile csv(String body) {
		return new MockMultipartFile("file", "rows.csv", "text/csv", body.getBytes(StandardCharsets.UTF_8));
	}

	private static MockMultipartFile xlsx(XSSFWorkbook workbook) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		workbook.write(out);
		workbook.close();
		return new MockMultipartFile("file", "rows.xlsx", "application/octet-stream", out.toByteArray());
	}

	@Test
	void anOversizedFileIsRefusedBeforeItIsRead() {
		byte[] big = new byte[5 * 1024 * 1024 + 1];
		java.util.Arrays.fill(big, (byte) 'a');
		assertThatThrownBy(() -> ImportFileParser.parse(new MockMultipartFile("file", "big.csv", "text/csv", big)))
				.isInstanceOf(ImportFileException.class).hasMessageContaining("too large");
	}

	@Test
	void csvWithTooManyRowsIsRefusedAndExactlyTheLimitIsAccepted() {
		ImportFileParser.configure(5L * 1024 * 1024, 50, 200, 30L * 1024 * 1024, 0.01);
		assertThatCode(() -> ImportFileParser.parse(csv(rows(50)))).doesNotThrowAnyException();
		assertThatThrownBy(() -> ImportFileParser.parse(csv(rows(51)))).isInstanceOf(ImportFileException.class)
				.hasMessageContaining("too many rows");
	}

	@Test
	void defaultRowLimitIsTenThousandDataRows() {
		assertThatCode(() -> ImportFileParser.parse(csv(rows(10_000)))).doesNotThrowAnyException();
		assertThatThrownBy(() -> ImportFileParser.parse(csv(rows(10_001)))).isInstanceOf(ImportFileException.class)
				.hasMessageContaining("too many rows");
	}

	@Test
	void xlsxWithTooManyRowsIsRefused() throws Exception {
		ImportFileParser.configure(5L * 1024 * 1024, 20, 200, 30L * 1024 * 1024, 0.01);
		assertThatCode(() -> ImportFileParser.parse(xlsx(workbookWithRows(20)))).doesNotThrowAnyException();
		MockMultipartFile tooMany = xlsx(workbookWithRows(21));
		assertThatThrownBy(() -> ImportFileParser.parse(tooMany)).isInstanceOf(ImportFileException.class)
				.hasMessageContaining("too many rows");
	}

	@Test
	void xlsxWithTooManyColumnsIsRefused() throws Exception {
		ImportFileParser.configure(5L * 1024 * 1024, 100, 5, 30L * 1024 * 1024, 0.01);
		XSSFWorkbook workbook = new XSSFWorkbook();
		var row = workbook.createSheet().createRow(0);
		for (int i = 0; i < 6; i++) {
			row.createCell(i).setCellValue("h" + i);
		}
		MockMultipartFile file = xlsx(workbook);
		assertThatThrownBy(() -> ImportFileParser.parse(file)).isInstanceOf(ImportFileException.class)
				.hasMessageContaining("too many columns");
	}

	@Test
	void aSmallValidXlsxStillParses() throws Exception {
		assertThat(ImportFileParser.parse(xlsx(workbookWithRows(3)))).hasSize(3);
	}

	/** A zip bomb: a few KB on disk that inflate by thousands of times. POI must refuse it rather than expand it. */
	@Test
	void anXlsxThatInflatesFarBeyondTheRatioIsRefused() throws Exception {
		XSSFWorkbook workbook = new XSSFWorkbook();
		var sheet = workbook.createSheet();
		for (int r = 0; r < 100; r++) {
			// distinct 32k strings so shared-strings can't dedupe them, and each is a single highly compressible run.
			sheet.createRow(r).createCell(0).setCellValue(String.valueOf((char) ('A' + r % 26)).repeat(32_000) + r);
		}
		MockMultipartFile bomb = xlsx(workbook);
		assertThat(bomb.getSize()).isLessThan(1024 * 1024);
		assertThatThrownBy(() -> ImportFileParser.parse(bomb)).isInstanceOf(ImportFileException.class)
				.hasMessageContaining("valid .xlsx");
	}

	@Test
	void anXlsxEntryLargerThanTheEntryCapIsRefused() throws Exception {
		ImportFileParser.configure(5L * 1024 * 1024, 10_000, 200, 10_000, 0.0001);
		XSSFWorkbook workbook = new XSSFWorkbook();
		var sheet = workbook.createSheet();
		for (int r = 0; r < 500; r++) {
			var row = sheet.createRow(r);
			for (int c = 0; c < 10; c++) {
				row.createCell(c).setCellValue("value " + r + "-" + c);
			}
		}
		MockMultipartFile big = xlsx(workbook);
		assertThatThrownBy(() -> ImportFileParser.parse(big)).isInstanceOf(ImportFileException.class);
	}

	private static String rows(int dataRows) {
		StringBuilder sb = new StringBuilder("name,code\n");
		for (int i = 0; i < dataRows; i++) {
			sb.append("n").append(i).append(",c").append(i).append('\n');
		}
		return sb.toString();
	}

	/** Header plus (dataRows) data rows, so a workbook of n data rows has n+1 physical rows. */
	private static XSSFWorkbook workbookWithRows(int dataRows) {
		XSSFWorkbook workbook = new XSSFWorkbook();
		var sheet = workbook.createSheet();
		var header = sheet.createRow(0);
		header.createCell(0).setCellValue("name");
		header.createCell(1).setCellValue("code");
		for (int i = 1; i <= dataRows; i++) {
			var row = sheet.createRow(i);
			row.createCell(0).setCellValue("n" + i);
			row.createCell(1).setCellValue("c" + i);
		}
		return workbook;
	}
}
