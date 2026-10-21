package com.operion.timetable;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class TimetableCsvTest {

	@Test
	void escapesCommasQuotesAndNeutralisesSpreadsheetFormulas() {
		assertThat(TimetableCsv.escape("Maths, Algebra")).isEqualTo("\"Maths, Algebra\"");
		assertThat(TimetableCsv.escape("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
		assertThat(TimetableCsv.escape("=HYPERLINK(\"x\")")).startsWith("\"'=HYPERLINK");
		assertThat(TimetableCsv.escape("+1")).isEqualTo("'+1");
		assertThat(TimetableCsv.escape(null)).isEmpty();
		assertThat(TimetableCsv.escape("Plain")).isEqualTo("Plain");
	}

	@Test
	void emptyGridListsEveryPeriodUnderMondayToFridayAndRepeatsBreakLabels() {
		Period teaching = new Period(1, "Period 1", LocalTime.of(9, 0), LocalTime.of(9, 45), false);
		Period lunch = new Period(2, "Lunch", LocalTime.of(9, 45), LocalTime.of(10, 15), true);
		String csv = TimetableCsv.grid(List.of(teaching, lunch), List.of(), e -> "x");
		assertThat(csv.split("\r\n")).containsExactly(
				"Period,Time,Monday,Tuesday,Wednesday,Thursday,Friday",
				"Period 1,09:00-09:45,,,,,",
				"Lunch,09:45-10:15,Lunch,Lunch,Lunch,Lunch,Lunch");
	}
}
