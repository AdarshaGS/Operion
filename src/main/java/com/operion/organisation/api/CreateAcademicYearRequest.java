package com.operion.organisation.api;

import java.time.LocalDate;

public record CreateAcademicYearRequest(String name, LocalDate startDate, LocalDate endDate) {
}
