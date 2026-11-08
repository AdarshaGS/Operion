package com.operion.organisation;

/**
 * The single place a write path asks "may this academic year still change?". Static and
 * deliberately not a Spring bean, so services that are built with {@code new} in tests keep their
 * constructors. Lives in the core organisation package, which must not depend on the school
 * vertical (see ArchitectureBoundaryTest), so it only knows {@link AcademicYear}; each vertical
 * walks its own entity chain to the year and passes it in. A null year (a chain that does not
 * resolve to one) is ignored - it is not this guard's job to validate the entity graph.
 */
public final class AcademicYearGuard {

	private AcademicYearGuard() {
	}

	/** @param action what was attempted, phrased to follow "... is not allowed", e.g. "adding a section". */
	public static void requireOpen(AcademicYear academicYear, String action) {
		if (academicYear != null) {
			academicYear.requireOpen(action);
		}
	}

	/** Checks every year given - used where a write spans two (e.g. promoting from one year into another). */
	public static void requireOpen(String action, AcademicYear... academicYears) {
		for (AcademicYear academicYear : academicYears) {
			requireOpen(academicYear, action);
		}
	}
}
