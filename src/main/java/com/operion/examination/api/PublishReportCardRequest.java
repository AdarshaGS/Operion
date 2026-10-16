package com.operion.examination.api;

import jakarta.validation.constraints.NotNull;

public record PublishReportCardRequest(@NotNull Long studentEnrollmentId, @NotNull Long gradingScaleId) {
}
