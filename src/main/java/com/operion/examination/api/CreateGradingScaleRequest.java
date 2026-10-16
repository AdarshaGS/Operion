package com.operion.examination.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateGradingScaleRequest(@NotBlank String name, boolean defaultScale, @NotEmpty List<@Valid @NotNull GradingBandEntry> bands) {
}
