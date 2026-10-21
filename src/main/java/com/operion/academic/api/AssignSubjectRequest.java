package com.operion.academic.api;

/** weeklyPeriods is optional; leaving it out means the subject has no weekly quota in this class. */
public record AssignSubjectRequest(Long subjectId, boolean mandatory, Integer weeklyPeriods) {
}
