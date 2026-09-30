package com.operion.platform.crm.api;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Exactly one of leadId / organisationId. occurredAt defaults to now; the author is the caller. */
public record CreateActivityRequest(Long leadId, Long organisationId, @NotNull String type, @NotBlank String body,
		Instant occurredAt) {
}
