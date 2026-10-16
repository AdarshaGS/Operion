package com.operion.onboarding.api;

import java.util.Map;

/** Answers keyed by question key (see GET /school-template/needs); an unanswered question uses its default. */
public record StarterPackAnswersRequest(Map<String, Boolean> answers) {
}
