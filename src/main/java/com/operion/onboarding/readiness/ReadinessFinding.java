package com.operion.onboarding.readiness;

/** One line of the go-live report. {@code fixPath} is the screen that resolves it (null when nothing is needed). */
public record ReadinessFinding(ReadinessStage stage, ReadinessLevel level, String message, String fixPath) {

	public static ReadinessFinding pass(ReadinessStage stage, String message) {
		return new ReadinessFinding(stage, ReadinessLevel.PASS, message, null);
	}

	public static ReadinessFinding warn(ReadinessStage stage, String message, String fixPath) {
		return new ReadinessFinding(stage, ReadinessLevel.WARN, message, fixPath);
	}

	public static ReadinessFinding fail(ReadinessStage stage, String message, String fixPath) {
		return new ReadinessFinding(stage, ReadinessLevel.FAIL, message, fixPath);
	}

	/** A pass when {@code ok}, otherwise a finding of {@code otherwise} severity. */
	public static ReadinessFinding of(boolean ok, ReadinessStage stage, ReadinessLevel otherwise, String okMessage, String problem, String fixPath) {
		if (ok) {
			return pass(stage, okMessage);
		}
		return new ReadinessFinding(stage, otherwise, problem, fixPath);
	}
}
