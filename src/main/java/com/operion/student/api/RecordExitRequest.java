package com.operion.student.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** No initiatedBy: the actor is always the authenticated user (TenantContext), never a client-supplied id. */
public record RecordExitRequest(
		@NotBlank String exitType, @NotNull LocalDate exitDate, String reason, String destinationSchool) {
}
