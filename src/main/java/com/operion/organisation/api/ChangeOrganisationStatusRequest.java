package com.operion.organisation.api;

import jakarta.validation.constraints.NotBlank;

public record ChangeOrganisationStatusRequest(@NotBlank String status) {
}
