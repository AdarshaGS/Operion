package com.operion.performance.api;

import jakarta.validation.constraints.NotBlank;

/** {@code status} only matters when updating a goal. */
public record GoalRequest(@NotBlank String title, String description, String status) {
}
