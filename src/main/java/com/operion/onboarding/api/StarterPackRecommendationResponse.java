package com.operion.onboarding.api;

import java.util.List;

import com.operion.onboarding.SchoolStarterPackService;

public record StarterPackRecommendationResponse(List<Module> modules) {

	/** {@code locked}: on because every school needs it, or because another selected module requires it. */
	public record Module(String code, String label, boolean enabled, boolean locked, String reason) {
	}

	public static StarterPackRecommendationResponse from(SchoolStarterPackService.Recommendation recommendation) {
		return new StarterPackRecommendationResponse(recommendation.modules().stream()
				.map(choice -> new Module(choice.module().name(), choice.module().label(), choice.enabled(), choice.locked(), choice.reason()))
				.toList());
	}
}
