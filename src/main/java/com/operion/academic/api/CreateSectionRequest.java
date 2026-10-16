package com.operion.academic.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateSectionRequest(@NotBlank String name, @Positive Integer capacity, String room) {
}
