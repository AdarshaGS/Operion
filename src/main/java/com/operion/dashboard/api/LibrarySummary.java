package com.operion.dashboard.api;

public record LibrarySummary(long activeBooks, long currentlyBorrowed, long overdueBorrows) {
}
