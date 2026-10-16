package com.operion.onboarding.api;

import java.util.List;

import com.operion.onboarding.SchoolStarterPackService;

public record StarterPackNeedsResponse(List<Question> questions, boolean canSelectModules) {

	public record Question(String key, String label, String helpText, boolean defaultYes) {
	}

	public static StarterPackNeedsResponse from(SchoolStarterPackService.Needs needs) {
		return new StarterPackNeedsResponse(
				needs.questions().stream().map(q -> new Question(q.key(), q.label(), q.helpText(), q.defaultYes())).toList(),
				needs.canSelectModules());
	}
}
