package com.operion.examination.api;

import java.util.List;

public record CreateGradingScaleRequest(String name, boolean defaultScale, List<GradingBandEntry> bands) {
}
