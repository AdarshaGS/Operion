package com.operion.modules.api;

import jakarta.validation.constraints.Size;

public record UpdateOrganisationModuleRequest(boolean enabled, @Size(max = 255) String note) {
}
