package com.operion.organisation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** The closed-year rule at its root: AcademicYear.requireOpen, the exception it throws, and the static guard around it. */
class AcademicYearGuardTest {

	private static AcademicYear openYear() {
		return new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30));
	}

	private static AcademicYear closedYear() {
		AcademicYear year = openYear();
		year.close();
		return year;
	}

	@Test
	void requireOpenAcceptsDraftAndActiveYears() {
		AcademicYear active = openYear();
		active.markCurrent();

		assertThatCode(() -> openYear().requireOpen("anything")).doesNotThrowAnyException();
		assertThatCode(() -> active.requireOpen("anything")).doesNotThrowAnyException();
	}

	@Test
	void requireOpenRejectsAClosedYearWithAHelpfulMessage() {
		assertThatThrownBy(() -> closedYear().requireOpen("adding a section"))
				.isInstanceOf(ClosedAcademicYearException.class)
				.hasMessage("Academic year 2025-2026 is closed; adding a section is not allowed");
	}

	@Test
	void theExceptionIsAnIllegalStateExceptionSoTheApiMapsItToConflict() {
		assertThat(new ClosedAcademicYearException("2025-2026", "x")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void guardDelegatesToTheYearAndIgnoresNull() {
		assertThatThrownBy(() -> AcademicYearGuard.requireOpen(closedYear(), "editing")).isInstanceOf(ClosedAcademicYearException.class);
		assertThatCode(() -> AcademicYearGuard.requireOpen(openYear(), "editing")).doesNotThrowAnyException();
		assertThatCode(() -> AcademicYearGuard.requireOpen((AcademicYear) null, "editing")).doesNotThrowAnyException();
	}

	@Test
	void guardWithSeveralYearsRejectsIfAnyIsClosed() {
		assertThatThrownBy(() -> AcademicYearGuard.requireOpen("promoting", openYear(), closedYear()))
				.isInstanceOf(ClosedAcademicYearException.class);
		assertThatCode(() -> AcademicYearGuard.requireOpen("promoting", openYear(), null, openYear())).doesNotThrowAnyException();
	}
}
