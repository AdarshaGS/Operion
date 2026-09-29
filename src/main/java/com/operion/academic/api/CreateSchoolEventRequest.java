package com.operion.academic.api;

import java.time.LocalDate;

/** campusId null = applies to every campus. */
public record CreateSchoolEventRequest(Long campusId, LocalDate date, String title, String description) {
}
