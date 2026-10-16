package com.operion.student.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record RecordExitRequest(
		@NotBlank String exitType, @NotNull LocalDate exitDate, String reason, String destinationSchool, Long initiatedBy) {
}
