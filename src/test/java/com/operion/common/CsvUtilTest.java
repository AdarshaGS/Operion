package com.operion.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CsvUtilTest {

	@Test
	void parsesPlainCommaSeparatedFields() {
		assertThat(CsvUtil.parseLine("firstName,lastName,email")).containsExactly("firstName", "lastName", "email");
	}

	@Test
	void parsesQuotedFieldsWithEmbeddedCommas() {
		assertThat(CsvUtil.parseLine("\"Rao, Jr.\",Asha,\"asha@example.com\"")).containsExactly("Rao, Jr.", "Asha", "asha@example.com");
	}

	@Test
	void parsesEscapedDoubleQuotesInsideAQuotedField() {
		assertThat(CsvUtil.parseLine("\"5'6\"\" tall\",Note")).containsExactly("5'6\" tall", "Note");
	}

	@Test
	void parsesEmptyTrailingField() {
		assertThat(CsvUtil.parseLine("a,b,")).containsExactly("a", "b", "");
	}
}
