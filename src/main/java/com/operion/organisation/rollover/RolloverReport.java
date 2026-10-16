package com.operion.organisation.rollover;

import java.util.List;

/** {@code dryRun}: nothing was saved. {@code unavailable}: steps left out because their module is switched off. */
public record RolloverReport(boolean dryRun, String sourceYear, String targetYear, List<RolloverStep> steps, List<String> unavailable) {
}
