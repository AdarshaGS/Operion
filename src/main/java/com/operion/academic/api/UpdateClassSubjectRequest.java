package com.operion.academic.api;

/** weeklyPeriods: omitted/null leaves the quota as it is, 0 clears it, a positive number sets it. */
public record UpdateClassSubjectRequest(boolean mandatory, Integer weeklyPeriods) {
}
