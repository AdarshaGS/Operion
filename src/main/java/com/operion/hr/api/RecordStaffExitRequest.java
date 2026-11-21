package com.operion.hr.api;

import java.time.LocalDate;

import jakarta.validation.constraints.Size;

/** The reason length matches staff_exits.reason (500). */
public record RecordStaffExitRequest(String exitType, LocalDate exitDate, @Size(max = 500) String reason, Long initiatedBy) {
}
