package com.operion.organisation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentNumberFormatterTest {

	@Test
	void padsSequenceToConfiguredWidth() {
		assertThat(DocumentNumberFormatter.format("INV-{AY}-{SEQ:6}", 1, "2025-2026", 2025)).isEqualTo("INV-2025-2026-000001");
	}

	@Test
	void defaultsSequenceWidthToOneWhenNoWidthGiven() {
		assertThat(DocumentNumberFormatter.format("STU-{SEQ}", 7, null, 2026)).isEqualTo("STU-7");
	}

	@Test
	void substitutesCalendarYear() {
		assertThat(DocumentNumberFormatter.format("STU-{YYYY}-{SEQ:4}", 12, null, 2026)).isEqualTo("STU-2026-0012");
	}

	@Test
	void leavesAcademicYearTokenUntouchedWhenLabelIsNull() {
		assertThat(DocumentNumberFormatter.format("STU-{AY}-{SEQ:4}", 1, null, 2026)).isEqualTo("STU-{AY}-0001");
	}
}
