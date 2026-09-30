package com.operion.privacy.api;

import java.time.LocalDate;

/** requestType: "ACCESS" | "EXPORT" | "CORRECTION" | "DELETION". receivedDate defaults to today
 * and dueDate to 30 days after it. */
public record OpenDataSubjectRequestRequest(Long personId, String requestType, String description, LocalDate receivedDate,
		LocalDate dueDate) {
}
