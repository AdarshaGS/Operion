package com.operion.examination.api;

import jakarta.validation.constraints.NotNull;

public record MarkEntry(@NotNull Long studentEnrollmentId, Double marksObtained, boolean absent, String remarks) {
}
