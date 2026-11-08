package com.operion.organisation;

import java.util.Optional;

/**
 * A module's veto on closing an academic year, e.g. students still enrolled in it. The core
 * organisation package cannot see the school vertical, so each module contributes its own bean
 * (same shape as the rollover contributors) and {@link AcademicYearService#close} asks them all.
 */
public interface AcademicYearClosureCheck {

	/** A human-readable reason the year cannot be closed yet, or empty if this module has no objection. */
	Optional<String> blockingReason(AcademicYear academicYear);
}
