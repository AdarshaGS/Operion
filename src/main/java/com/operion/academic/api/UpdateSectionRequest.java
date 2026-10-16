package com.operion.academic.api;

import jakarta.validation.constraints.Positive;

public record UpdateSectionRequest(String name, @Positive Integer capacity, String room) {
}
