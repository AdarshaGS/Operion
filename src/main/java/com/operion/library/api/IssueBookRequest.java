package com.operion.library.api;

import java.time.LocalDate;

public record IssueBookRequest(Long bookCopyId, Long borrowerPersonId, LocalDate borrowedDate, LocalDate dueDate) {
}
