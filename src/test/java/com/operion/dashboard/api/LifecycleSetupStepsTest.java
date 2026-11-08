package com.operion.dashboard.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The pure decision rules behind the school-lifecycle card (see SetupChecklist). */
class LifecycleSetupStepsTest {

	@Test
	void institutionProfileNeedsContactNameEmailAndTimezone() {
		assertThat(DashboardController.isInstitutionProfileConfigured("Owner", "owner@school.test", "Asia/Kolkata")).isTrue();
		assertThat(DashboardController.isInstitutionProfileConfigured(null, "owner@school.test", "Asia/Kolkata")).isFalse();
		assertThat(DashboardController.isInstitutionProfileConfigured("Owner", " ", "Asia/Kolkata")).isFalse();
		assertThat(DashboardController.isInstitutionProfileConfigured("Owner", "owner@school.test", "")).isFalse();
	}

	@Test
	void academicYearIsSetWhenCurrent() {
		assertThat(DashboardController.isAcademicYearSet(true)).isTrue();
	}

	@Test
	void academicYearNotSetWithoutACurrentYear() {
		// A closed (or draft-only) school has no current year - closing the only year reopens this step.
		assertThat(DashboardController.isAcademicYearSet(false)).isFalse();
	}

	@Test
	void classStructureNeedsBothAClassAndASection() {
		assertThat(DashboardController.isClassStructureCreated(true, true)).isTrue();
		assertThat(DashboardController.isClassStructureCreated(true, false)).isFalse();
		assertThat(DashboardController.isClassStructureCreated(false, true)).isFalse();
		assertThat(DashboardController.isClassStructureCreated(false, false)).isFalse();
	}

	@Test
	void admissionsDoneWhenAnApplicantWasAdmitted() {
		assertThat(DashboardController.isAdmissionsStepDone(true, false)).isTrue();
	}

	@Test
	void admissionsDoneForSchoolsThatImportedStudentsDirectly() {
		assertThat(DashboardController.isAdmissionsStepDone(false, true)).isTrue();
	}

	@Test
	void admissionsNotDoneWithNeitherApplicantsNorStudents() {
		assertThat(DashboardController.isAdmissionsStepDone(false, false)).isFalse();
	}

	@Test
	void subjectsMappedWhenEveryActiveClassHasASubject() {
		assertThat(DashboardController.areSubjectsMappedToEveryClass(3, 0)).isTrue();
	}

	@Test
	void subjectsNotMappedWhileAnyActiveClassHasNone() {
		// One mapped class out of three is not a complete configuration.
		assertThat(DashboardController.areSubjectsMappedToEveryClass(3, 2)).isFalse();
		assertThat(DashboardController.areSubjectsMappedToEveryClass(3, 3)).isFalse();
		assertThat(DashboardController.areSubjectsMappedToEveryClass(1, 1)).isFalse();
	}

	@Test
	void subjectsNotMappedWithoutAnyClass() {
		// "Every class has a subject" must not hold vacuously on a school with no classes.
		assertThat(DashboardController.areSubjectsMappedToEveryClass(0, 0)).isFalse();
	}
}
