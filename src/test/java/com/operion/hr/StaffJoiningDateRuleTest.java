package com.operion.hr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import com.operion.common.InvalidRequestException;
import org.junit.jupiter.api.Test;

/** B4: a joining date must lie between 1950-01-01 and today + 365 days, both inclusive. The rule takes "today" as an argument
 * (the app has no Clock bean; HrService passes LocalDate.now()), so the limits are exercised against a fixed day here. */
class StaffJoiningDateRuleTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

	@Test
	void bothLimitsAreAccepted() {
		assertThatCode(() -> HrService.requireValidDateOfJoining(LocalDate.of(1950, 1, 1), TODAY)).doesNotThrowAnyException();
		assertThatCode(() -> HrService.requireValidDateOfJoining(TODAY.plusDays(365), TODAY)).doesNotThrowAnyException();
		assertThatCode(() -> HrService.requireValidDateOfJoining(TODAY, TODAY)).doesNotThrowAnyException();
	}

	@Test
	void oneDayBeyondEitherLimitIsRejectedWithAClearMessage() {
		assertThatThrownBy(() -> HrService.requireValidDateOfJoining(LocalDate.of(1949, 12, 31), TODAY))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("The date of joining cannot be before 1950-01-01");
		assertThatThrownBy(() -> HrService.requireValidDateOfJoining(TODAY.plusDays(366), TODAY))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("The date of joining cannot be more than 365 days in the future (latest " + TODAY.plusDays(365) + ")");
	}

	@Test
	void aMissingDateIsRejected() {
		assertThatThrownBy(() -> HrService.requireValidDateOfJoining(null, TODAY)).isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void theLimitsAreTheDocumentedConstants() {
		assertThat(HrService.EARLIEST_DATE_OF_JOINING).isEqualTo(LocalDate.of(1950, 1, 1));
		assertThat(HrService.MAX_DAYS_JOINING_AHEAD).isEqualTo(365);
	}
}
