package com.operion.common.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ImportFileParserTest {

	@Test
	void utf8BomIsNotPartOfTheFirstHeader() {
		byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
		byte[] body = "name,code\nMaths,MA\n".getBytes(StandardCharsets.UTF_8);
		byte[] content = new byte[bom.length + body.length];
		System.arraycopy(bom, 0, content, 0, bom.length);
		System.arraycopy(body, 0, content, bom.length, body.length);

		List<Map<String, String>> rows = ImportFileParser.parse(new MockMultipartFile("file", "subjects.csv", "text/csv", content));

		assertThat(rows).hasSize(1);
		assertThat(rows.get(0)).containsEntry("name", "Maths");
	}

	@Test
	void unusableFilesAreImportFileExceptions() {
		assertThatThrownBy(() -> ImportFileParser.parse(null)).isInstanceOf(ImportFileException.class);
		assertThatThrownBy(() -> ImportFileParser.parse(new MockMultipartFile("file", "a.csv", "text/csv", new byte[0])))
				.isInstanceOf(ImportFileException.class);
		assertThatThrownBy(() -> ImportFileParser.parse(new MockMultipartFile("file", "a.pdf", "application/pdf", "x".getBytes())))
				.isInstanceOf(ImportFileException.class).hasMessageContaining("Unsupported file type");
		assertThatThrownBy(() -> ImportFileParser.parse(new MockMultipartFile("file", "fake.xlsx", "application/octet-stream", "not a workbook".getBytes())))
				.isInstanceOf(ImportFileException.class);
	}

	@Test
	void rowErrorsDoNotLeakInternals() {
		assertThat(ImportRowErrors.message(new NumberFormatException("For input string: \"plenty\""))).doesNotContain("plenty");
		assertThat(ImportRowErrors.message(new IllegalArgumentException("No enum constant com.operion.transport.VehicleType.X")))
				.doesNotContain("com.operion");
		assertThat(ImportRowErrors.message(new RuntimeException("could not execute statement; SQL [insert into subjects ...]")))
				.doesNotContain("insert into");
		assertThat(ImportRowErrors.message(new IllegalArgumentException("code is required"))).isEqualTo("code is required");
	}
}
