package com.operion.academic;

import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearGuard;

/**
 * Walks the academic-structure chain (section -> class -> year, class subject -> class -> year)
 * to the owning academic year and hands it to {@link AcademicYearGuard}. Kept here, not on the
 * core guard, because the core organisation package must not depend on the school vertical.
 * Every relation on the chain is a mandatory, eagerly fetched {@code @ManyToOne}; a null link
 * (only possible with a hand-built entity) resolves to "no year" and is ignored.
 */
public final class AcademicStructureYearGuard {

	private AcademicStructureYearGuard() {
	}

	public static void requireOpen(SchoolClass schoolClass, String action) {
		AcademicYearGuard.requireOpen(yearOf(schoolClass), action);
	}

	public static void requireOpen(Section section, String action) {
		AcademicYearGuard.requireOpen(yearOf(section), action);
	}

	public static void requireOpen(ClassSubject classSubject, String action) {
		AcademicYearGuard.requireOpen(classSubject == null ? null : yearOf(classSubject.getSchoolClass()), action);
	}

	public static void requireOpen(TeacherAssignment assignment, String action) {
		AcademicYearGuard.requireOpen(assignment == null ? null : assignment.getAcademicYear(), action);
	}

	public static AcademicYear yearOf(SchoolClass schoolClass) {
		return schoolClass == null ? null : schoolClass.getAcademicYear();
	}

	public static AcademicYear yearOf(Section section) {
		return section == null ? null : yearOf(section.getSchoolClass());
	}
}
