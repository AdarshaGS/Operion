package com.operion.library.api;

public record LibraryStatusSummaryResponse(long available, long issued, long overdue, long lost, long damaged) {
}
