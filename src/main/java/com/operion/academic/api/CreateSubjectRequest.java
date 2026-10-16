package com.operion.academic.api;

import jakarta.validation.constraints.NotBlank;

public record CreateSubjectRequest(@NotBlank String name, String code) {
}
