package com.operion.library.api;

import java.math.BigDecimal;

import com.operion.library.LibrarySettings;

public record LibrarySettingsResponse(int maxLoansStudent, int maxLoansStaff, int borrowingPeriodDaysStudent,
		int borrowingPeriodDaysStaff, boolean blockIssueOnOverdue,
		BigDecimal fineRatePerDay) {

	public static LibrarySettingsResponse from(LibrarySettings settings) {
		return new LibrarySettingsResponse(settings.getMaxLoansStudent(), settings.getMaxLoansStaff(),
				settings.getBorrowingPeriodDaysStudent(), settings.getBorrowingPeriodDaysStaff(), settings.isBlockIssueOnOverdue(),
				settings.getFineRatePerDay());
	}

	public static LibrarySettingsResponse defaults() {
		return new LibrarySettingsResponse(LibrarySettings.DEFAULT_MAX_LOANS_STUDENT, LibrarySettings.DEFAULT_MAX_LOANS_STAFF,
				LibrarySettings.DEFAULT_BORROWING_PERIOD_DAYS_STUDENT, LibrarySettings.DEFAULT_BORROWING_PERIOD_DAYS_STAFF,
				LibrarySettings.DEFAULT_BLOCK_ISSUE_ON_OVERDUE, LibrarySettings.DEFAULT_FINE_RATE_PER_DAY);
	}
}
