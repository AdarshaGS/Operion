package com.operion.hr.api;

import jakarta.validation.constraints.Size;

/** The name length matches leave_types.name (100). */
public record UpdateLeaveTypeRequest(@Size(max = 100) String name, Double defaultAnnualDays) {
}
