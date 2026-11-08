package com.operion.onboarding;

import java.util.Arrays;
import java.util.Optional;

/** The steps of the Structure Setup wizard that are tracked as completed (the Review step is not - it is the end). */
public enum SetupStep {

	PROFILE("profile"),
	BUSINESS("business"),
	CAMPUSES("campuses");

	private final String key;

	SetupStep(String key) {
		this.key = key;
	}

	public String key() {
		return key;
	}

	public static Optional<SetupStep> fromKey(String key) {
		return Arrays.stream(values()).filter(step -> step.key.equals(key)).findFirst();
	}
}
