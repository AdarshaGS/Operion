package com.operion.dashboard.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.operion.academic.ClassSubject;
import com.operion.academic.ClassSubjectRepository;
import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.Subject;
import com.operion.academic.SubjectRepository;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The "Subject configuration" setup step runs against the latest academic year (current, else most recently
 * closed). Runs the real repository queries (H2, tenant filter active) through the controller's decision helper. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SubjectConfigurationLatestYearTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private AcademicYearRepository academicYearRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private GradeLevelRepository gradeLevelRepository;

	@Autowired
	private SchoolClassRepository schoolClassRepository;

	@Autowired
	private SubjectRepository subjectRepository;

	@Autowired
	private ClassSubjectRepository classSubjectRepository;

	private Campus campus;
	private GradeLevel grade;
	private Subject subject;

	@BeforeEach
	void newTenant() {
		Organisation organisation = organisationRepository.save(
				new Organisation("Test School", "Test School Trust", "subject-config-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);
		campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		grade = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		subject = subjectRepository.save(new Subject("Mathematics", "MATH"));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private AcademicYear currentYear(String name, int startYear) {
		AcademicYear year = new AcademicYear(name, LocalDate.of(startYear, 6, 1), LocalDate.of(startYear + 1, 4, 30));
		year.markCurrent();
		return academicYearRepository.save(year);
	}

	private AcademicYear closedYear(String name, LocalDate start, LocalDate end) {
		AcademicYear year = new AcademicYear(name, start, end);
		year.close();
		return academicYearRepository.save(year);
	}

	private SchoolClass classIn(AcademicYear year, boolean withSubject) {
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(year, campus, grade, null));
		if (withSubject) {
			classSubjectRepository.save(new ClassSubject(schoolClass, subject, true));
		}
		return schoolClass;
	}

	private boolean subjectsMapped() {
		return DashboardController.areSubjectsMappedInLatestYear(academicYearRepository, schoolClassRepository);
	}

	@Test
	void noAcademicYearsMeansNotDone() {
		assertThat(subjectsMapped()).isFalse();
	}

	@Test
	void draftOnlyYearMeansNotDone() {
		AcademicYear draft = academicYearRepository.save(
				new AcademicYear("2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 4, 30)));
		classIn(draft, true);

		assertThat(subjectsMapped()).isFalse();
	}

	@Test
	void currentYearWithEveryClassMappedIsDone() {
		AcademicYear current = currentYear("2026-2027", 2026);
		classIn(current, true);
		classIn(current, true);

		assertThat(subjectsMapped()).isTrue();
	}

	@Test
	void currentYearWithAnUnmappedClassIsNotDone() {
		AcademicYear current = currentYear("2026-2027", 2026);
		classIn(current, true);
		classIn(current, false);

		assertThat(subjectsMapped()).isFalse();
	}

	@Test
	void currentYearWithoutClassesIsNotDone() {
		currentYear("2026-2027", 2026);

		assertThat(subjectsMapped()).isFalse();
	}

	@Test
	void currentYearWinsOverAClosedYear() {
		AcademicYear closed = closedYear("2024-2025", LocalDate.of(2024, 6, 1), LocalDate.of(2025, 4, 30));
		classIn(closed, true);
		classIn(currentYear("2026-2027", 2026), false);

		assertThat(subjectsMapped()).isFalse();
	}

	@Test
	void noCurrentYearFallsBackToTheClosedYear() {
		AcademicYear closed = closedYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30));
		classIn(closed, true);

		assertThat(subjectsMapped()).isTrue();
	}

	@Test
	void closedYearWithAnUnmappedClassIsNotDone() {
		AcademicYear closed = closedYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30));
		classIn(closed, true);
		classIn(closed, false);

		assertThat(subjectsMapped()).isFalse();
	}

	@Test
	void mostRecentlyClosedYearByEndDateIsUsed() {
		AcademicYear older = closedYear("2023-2024", LocalDate.of(2023, 6, 1), LocalDate.of(2024, 4, 30));
		classIn(older, false);
		AcademicYear newer = closedYear("2024-2025", LocalDate.of(2024, 6, 1), LocalDate.of(2025, 4, 30));
		classIn(newer, true);

		assertThat(subjectsMapped()).isTrue();
	}

	@Test
	void closedYearsWithTheSameEndDateTieBreakOnTheHigherId() {
		LocalDate start = LocalDate.of(2025, 6, 1);
		LocalDate end = LocalDate.of(2026, 4, 30);
		AcademicYear first = closedYear("2025-2026 a", start, end);
		classIn(first, false);
		AcademicYear second = closedYear("2025-2026 b", start, end);
		classIn(second, true);

		assertThat(subjectsMapped()).isTrue();
	}

	@Test
	void otherTenantsYearsAreIgnored() {
		classIn(currentYear("2026-2027", 2026), true);
		Long thisTenant = TenantContext.getOrganisationId();

		Organisation other = organisationRepository.save(
				new Organisation("Other School", "Other Trust", "subject-config-other-" + System.nanoTime()));
		TenantContext.set(other.getId(), null);

		assertThat(subjectsMapped()).isFalse();

		TenantContext.set(thisTenant, null);
		assertThat(subjectsMapped()).isTrue();
	}
}
