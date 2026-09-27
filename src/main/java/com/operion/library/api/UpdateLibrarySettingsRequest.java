package com.operion.library.api;

public record UpdateLibrarySettingsRequest(int maxLoansStudent, int maxLoansStaff, int borrowingPeriodDaysStudent,
		int borrowingPeriodDaysStaff, boolean blockIssueOnOverdue) {
}
