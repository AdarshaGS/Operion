package com.operion.library.api;

import java.time.LocalDate;

public record AddBookCopyRequest(Long campusId, String accessionNumber, LocalDate acquiredDate) {
}
