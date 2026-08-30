package com.operion.hr.api;

import java.time.LocalDate;

public record RecordStaffExitRequest(String exitType, LocalDate exitDate, String reason, Long initiatedBy) {
}
