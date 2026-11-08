package com.operion.organisation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.operion.common.InvalidRequestException;
import org.junit.jupiter.api.Test;

class DocumentNumberFormatterWidthCapTest {

	@Test
	void anAbsurdWidthIsClampedWhenFormattingInsteadOfAllocatingIt() {
		String result = DocumentNumberFormatter.format("X-{SEQ:2000000000}", 5, null, 2026);
		assertThat(result).isEqualTo("X-000000000005");
		assertThat(DocumentNumberFormatter.format("X-{SEQ:99999999999999999999999}", 5, null, 2026)).isEqualTo("X-000000000005");
	}

	@Test
	void theMaximumWidthIsStillHonouredAndZeroBecomesOne() {
		assertThat(DocumentNumberFormatter.format("{SEQ:12}", 7, null, 2026)).isEqualTo("000000000007");
		assertThat(DocumentNumberFormatter.format("{SEQ:13}", 7, null, 2026)).isEqualTo("000000000007");
		assertThat(DocumentNumberFormatter.format("{SEQ:0}", 7, null, 2026)).isEqualTo("7");
	}

	@Test
	void requireValidAcceptsOrdinaryTemplates() {
		assertThatCode(() -> {
			DocumentNumberFormatter.requireValid("INV-{AY}-{SEQ:6}");
			DocumentNumberFormatter.requireValid("STU-{SEQ}");
			DocumentNumberFormatter.requireValid("{SEQ:12}");
			DocumentNumberFormatter.requireValid(null);
		}).doesNotThrowAnyException();
	}

	@Test
	void requireValidRejectsTooWideOrZeroWidths() {
		assertThatThrownBy(() -> DocumentNumberFormatter.requireValid("X-{SEQ:13}")).isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> DocumentNumberFormatter.requireValid("X-{SEQ:2000000000}")).isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> DocumentNumberFormatter.requireValid("X-{SEQ:99999999999999999999999}")).isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> DocumentNumberFormatter.requireValid("X-{SEQ:0}")).isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> DocumentNumberFormatter.requireValid("{SEQ:4}-{SEQ:50}")).isInstanceOf(InvalidRequestException.class);
	}
}
