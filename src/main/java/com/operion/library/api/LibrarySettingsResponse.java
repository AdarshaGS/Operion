package com.operion.library.api;

import com.operion.library.LibrarySettings;

public record LibrarySettingsResponse(int maxLoansStudent, int maxLoansStaff, int borrowingPeriodDaysStudent,
		int borrowingPeriodDaysStaff, boolean blockIssueOnOverdue) {

	public static LibrarySettingsResponse from(LibrarySettings settings) {
		return new LibrarySettingsResponse(settings.getMaxLoansStudent(), settings.getMaxLoansStaff(),
				settings.getBorrowingPeriodDaysStudent(), settings.getBorrowingPeriodDaysStaff(), settings.isBlockIssueOnOverdue());
	}

	public static LibrarySettingsResponse defaults() {
		return new LibrarySettingsResponse(LibrarySettings.DEFAULT_MAX_LOANS_STUDENT, LibrarySettings.DEFAULT_MAX_LOANS_STAFF,
				LibrarySettings.DEFAULT_BORROWING_PERIOD_DAYS_STUDENT, LibrarySettings.DEFAULT_BORROWING_PERIOD_DAYS_STAFF,
				LibrarySettings.DEFAULT_BLOCK_ISSUE_ON_OVERDUE);
	}
}
