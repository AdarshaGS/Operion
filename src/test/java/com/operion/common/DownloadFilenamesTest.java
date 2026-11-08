package com.operion.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DownloadFilenamesTest {

	@Test
	void anOrdinaryNameIsKept() {
		assertThat(DownloadFilenames.attachment("UDISE-2026_v1", "csv")).isEqualTo("attachment; filename=\"UDISE-2026_v1.csv\"");
	}

	@Test
	void quotesAndLineBreaksCannotEscapeTheHeaderValue() {
		String header = DownloadFilenames.attachment("x\"\r\nSet-Cookie: a=b\r\n", "csv");
		assertThat(header).doesNotContain("\r").doesNotContain("\n");
		assertThat(header.substring(header.indexOf("filename="))).matches("filename=\"[A-Za-z0-9._-]+\"");
	}

	@Test
	void pathSeparatorsAndLeadingDotsAreNeutralised() {
		String header = DownloadFilenames.attachment("../../etc/passwd", "csv");
		assertThat(header).doesNotContain("/").doesNotContain("\\").doesNotContain("..");
		assertThat(DownloadFilenames.attachment("..\\..\\boot", "csv")).doesNotContain("\\\\").doesNotContain("..");
	}

	@Test
	void emptyOrNullFallsBackAndLongNamesAreBounded() {
		assertThat(DownloadFilenames.attachment(null, "csv")).isEqualTo("attachment; filename=\"download.csv\"");
		assertThat(DownloadFilenames.attachment("", "csv")).isEqualTo("attachment; filename=\"download.csv\"");
		assertThat(DownloadFilenames.attachment("a".repeat(500), "csv").length()).isLessThan(130);
	}
}
