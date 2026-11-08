package com.operion.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class CsvUtilFormulaInjectionTest {

	@Test
	void prefixesTextCellsThatAreFormulaTriggers() {
		assertThat(CsvUtil.formatLine(List.of("=HYPERLINK(\"http://evil\")", "+cmd|' /C calc'!A0", "-2+3", "@SUM(A1)")))
				.isEqualTo("\"'=HYPERLINK(\"\"http://evil\"\")\",'+cmd|' /C calc'!A0,'-2+3,'@SUM(A1)");
	}

	@Test
	void prefixesCellsStartingWithTabCarriageReturnOrLineFeed() {
		assertThat(CsvUtil.formatLine(List.of("\t=1+1"))).isEqualTo("'\t=1+1");
		assertThat(CsvUtil.formatLine(List.of("\r=1+1"))).isEqualTo("\"'\r=1+1\"");
		assertThat(CsvUtil.formatLine(List.of("\n=1+1"))).isEqualTo("\"'\n=1+1\"");
	}

	@Test
	void leavesPlainNumbersAndOrdinaryTextAlone() {
		assertThat(CsvUtil.formatLine(List.of("-5", "-3.25", "42", "Asha", "a=b", "x@y.com", "1+1", ""))).isEqualTo("-5,-3.25,42,Asha,a=b,x@y.com,1+1,");
	}

	@Test
	void anEquationDisguisedAsANumberIsStillNeutralised() {
		assertThat(CsvUtil.formatLine(List.of("-1+1", "-1e5", "-5 ", "--5"))).isEqualTo("'-1+1,'-1e5,'-5 ,'--5");
	}

	@Test
	void nullsBecomeEmptyAndExistingQuotingStillApplies() {
		assertThat(CsvUtil.formatLine(Arrays.asList(null, "a,b", "say \"hi\""))).isEqualTo(",\"a,b\",\"say \"\"hi\"\"\"");
	}
}
