package com.operion.examination.api;

import java.util.List;

import com.operion.examination.GradingScale;
import com.operion.examination.GradingScaleBand;

public record GradingScaleResponse(Long id, String name, boolean defaultScale, List<GradingBandEntry> bands) {

	static GradingScaleResponse from(GradingScale scale, List<GradingScaleBand> bands) {
		return new GradingScaleResponse(scale.getId(), scale.getName(), scale.isDefaultScale(),
				bands.stream().map(band -> new GradingBandEntry(band.getGrade(), band.getMinPercentage(), band.getRemark())).toList());
	}
}
