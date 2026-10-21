package com.operion.performance.api;

import jakarta.validation.constraints.NotNull;

/** {@code source} (MANAGER or PEER) is required when rating another teacher and ignored for a self-appraisal. */
public record FeedbackRequest(String source, @NotNull Integer rating, String comment) {
}
