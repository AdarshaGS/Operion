package com.operion.organisation;

import java.time.LocalDate;

/** Optional first-academic-year input for {@link OrganisationService#provision}. */
public record AcademicYearDetails(String name, LocalDate startDate, LocalDate endDate) {
}
