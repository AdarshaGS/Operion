package com.operion.examination.api;

public record UpdateExaminationSettingsRequest(boolean rankingEnabled, String passFailStrategy, double minimumAggregatePercentage) {
}
