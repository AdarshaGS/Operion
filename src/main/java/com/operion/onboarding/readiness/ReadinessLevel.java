package com.operion.onboarding.readiness;

/** FAIL blocks go-live, WARN is worth a look, PASS needs nothing. Declared worst-last so ordinal comparison finds the worst. */
public enum ReadinessLevel {
	PASS, WARN, FAIL
}
