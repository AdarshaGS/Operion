package com.operion.platform.crm.api;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Exactly one of leadId / organisationId. assigneeId defaults to the caller. */
public record CreateCrmTaskRequest(Long leadId, Long organisationId, @NotBlank String title, @NotNull Instant dueAt,
		Long assigneeId) {
}
