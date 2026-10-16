package com.operion.onboarding.readiness;

/** The chain a go-live check walks for every enabled module: switched on, then configured, then holding data, then reachable by someone. */
public enum ReadinessStage {
	CONFIGURATION, DATA, ACCESS
}
