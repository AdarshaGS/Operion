package com.operion.document.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class DateTextTest {

	@Test
	void formatsDatesAndSpellsThemOut() {
		assertThat(DateText.format(LocalDate.of(2012, 3, 5))).isEqualTo("5 March 2012");
		assertThat(DateText.inWords(LocalDate.of(2012, 3, 5))).isEqualTo("Fifth March Two Thousand Twelve");
		assertThat(DateText.inWords(LocalDate.of(1999, 12, 31))).isEqualTo("Thirty First December One Thousand Nine Hundred Ninety Nine");
		assertThat(DateText.format(null)).isNull();
	}

	@Test
	void ordinalsCoverEveryDayOfTheMonth() {
		assertThat(DateText.ordinal(1)).isEqualTo("First");
		assertThat(DateText.ordinal(2)).isEqualTo("Second");
		assertThat(DateText.ordinal(3)).isEqualTo("Third");
		assertThat(DateText.ordinal(8)).isEqualTo("Eighth");
		assertThat(DateText.ordinal(9)).isEqualTo("Ninth");
		assertThat(DateText.ordinal(11)).isEqualTo("Eleventh");
		assertThat(DateText.ordinal(12)).isEqualTo("Twelfth");
		assertThat(DateText.ordinal(20)).isEqualTo("Twentieth");
		assertThat(DateText.ordinal(22)).isEqualTo("Twenty Second");
		assertThat(DateText.ordinal(30)).isEqualTo("Thirtieth");
	}
}
