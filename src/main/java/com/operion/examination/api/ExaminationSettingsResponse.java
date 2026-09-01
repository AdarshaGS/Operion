package com.operion.examination.api;

import com.operion.examination.ExaminationSettings;

public record ExaminationSettingsResponse(boolean rankingEnabled, String passFailStrategy, double minimumAggregatePercentage) {

	static ExaminationSettingsResponse from(ExaminationSettings settings) {
		return new ExaminationSettingsResponse(
				settings.isRankingEnabled(), settings.getPassFailStrategy().name(), settings.getMinimumAggregatePercentage());
	}

	static ExaminationSettingsResponse defaults() {
		return new ExaminationSettingsResponse(ExaminationSettings.DEFAULT_RANKING_ENABLED,
				ExaminationSettings.DEFAULT_PASS_FAIL_STRATEGY.name(), ExaminationSettings.DEFAULT_MINIMUM_AGGREGATE_PERCENTAGE);
	}
}
