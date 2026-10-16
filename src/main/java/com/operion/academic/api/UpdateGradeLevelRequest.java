package com.operion.academic.api;

import jakarta.validation.constraints.PositiveOrZero;

/** Partial update: a field left out (null) is left unchanged. */
public record UpdateGradeLevelRequest(String name, @PositiveOrZero Integer sequenceOrder, String stage) {
}
