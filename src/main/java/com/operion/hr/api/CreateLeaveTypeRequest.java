package com.operion.hr.api;

import jakarta.validation.constraints.Size;

/** Lengths match leave_types.code (30) and leave_types.name (100). */
public record CreateLeaveTypeRequest(@Size(max = 30) String code, @Size(max = 100) String name, Double defaultAnnualDays) {
}
