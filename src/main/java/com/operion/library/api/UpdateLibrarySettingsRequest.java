package com.operion.library.api;

import java.math.BigDecimal;

public record UpdateLibrarySettingsRequest(int maxLoansStudent, int maxLoansStaff, int borrowingPeriodDaysStudent,
		int borrowingPeriodDaysStaff, boolean blockIssueOnOverdue,
		BigDecimal fineRatePerDay) {
}
