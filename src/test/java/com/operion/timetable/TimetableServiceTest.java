package com.operion.timetable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import com.operion.academic.AcademicService;
import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.Subject;
import com.operion.academic.SubjectRepository;
import com.operion.academic.TeacherAssignmentType;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
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

/** Period rules and the two clash rules from #212 (section double-booking, teacher double-booking). */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, TimetableService.class, AcademicService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TimetableServiceTest {

	@Autowired private TimetableService timetableService;
	@Autowired private AcademicService academicService;
	@Autowired private TimetableEntryRepository entryRepository;
	@Autowired private PeriodRepository periodRepository;
	@Autowired private OrganisationRepository organisationRepository;
	@Autowired private CampusRepository campusRepository;
	@Autowired private AcademicYearRepository academicYearRepository;
	@Autowired private PersonRepository personRepository;
	@Autowired private GradeLevelRepository gradeLevelRepository;
	@Autowired private SchoolClassRepository schoolClassRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private SubjectRepository subjectRepository;

	private Section sectionA;
	private Section sectionB;
	private Subject maths;
	private Subject science;
	private Person asha;
	private Person bala;
	private Period period1;
	private Period period2;
	private Period lunch;

	@BeforeEach
	void setUp() {
		Organisation organisation = organisationRepository.save(
				new Organisation("Test School", "Test School Trust", "tt-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);

		AcademicYear year = academicYearRepository.save(
				new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(year, campus, grade, null));
		sectionA = sectionRepository.save(new Section(schoolClass, "A", 40, null));
		sectionB = sectionRepository.save(new Section(schoolClass, "B", 40, null));
		maths = subjectRepository.save(new Subject("Maths", "MATH"));
		science = subjectRepository.save(new Subject("Science", "SCI"));
		asha = personRepository.save(new Person("Asha", "Rao"));
		bala = personRepository.save(new Person("Bala", "Iyer"));

		LocalDate start = LocalDate.of(2025, 6, 1);
		academicService.assignTeacher(sectionA, maths, asha, TeacherAssignmentType.SUBJECT, start);
		academicService.assignTeacher(sectionB, maths, asha, TeacherAssignmentType.SUBJECT, start);
		academicService.assignTeacher(sectionA, science, bala, TeacherAssignmentType.SUBJECT, start);

		period1 = timetableService.createPeriod(1, "Period 1", LocalTime.of(9, 0), LocalTime.of(9, 45), false);
		period2 = timetableService.createPeriod(2, "Period 2", LocalTime.of(9, 45), LocalTime.of(10, 30), false);
		lunch = timetableService.createPeriod(3, "Lunch", LocalTime.of(10, 30), LocalTime.of(11, 0), true);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void periodsListInOrdinalOrderAndRejectDuplicateOrdinalOrBackwardsTimes() {
		assertThat(timetableService.listPeriods()).extracting(Period::getLabel)
				.containsExactly("Period 1", "Period 2", "Lunch");
		assertThatThrownBy(() -> timetableService.createPeriod(1, "Dup", LocalTime.of(12, 0), LocalTime.of(13, 0), false))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> timetableService.createPeriod(9, "Bad", LocalTime.of(13, 0), LocalTime.of(12, 0), false))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void setEntryPlacesASlotAndReplacingItDoesNotClashWithItself() {
		timetableService.setEntry(sectionA, maths, asha, DayOfWeek.MONDAY, period1);
		timetableService.setEntry(sectionA, science, bala, DayOfWeek.MONDAY, period1);

		assertThat(entryRepository.findBySectionId(sectionA.getId())).hasSize(1)
				.first().satisfies(entry -> assertThat(entry.getSubject().getId()).isEqualTo(science.getId()));
	}

	@Test
	void teacherCannotBeDoubleBookedInTheSamePeriodAcrossSections() {
		timetableService.setEntry(sectionA, maths, asha, DayOfWeek.MONDAY, period1);

		assertThatThrownBy(() -> timetableService.setEntry(sectionB, maths, asha, DayOfWeek.MONDAY, period1))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("already booked");
		assertThat(entryRepository.findBySectionId(sectionB.getId())).isEmpty();

		// Different period, or different day, is fine.
		timetableService.setEntry(sectionB, maths, asha, DayOfWeek.MONDAY, period2);
		timetableService.setEntry(sectionB, maths, asha, DayOfWeek.TUESDAY, period1);
		assertThat(entryRepository.findBySectionId(sectionB.getId())).hasSize(2);
	}

	@Test
	void breakPeriodsRejectEntriesAndCannotBecomeBreaksOnceUsed() {
		assertThatThrownBy(() -> timetableService.setEntry(sectionA, maths, asha, DayOfWeek.MONDAY, lunch))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("break");

		timetableService.setEntry(sectionA, maths, asha, DayOfWeek.MONDAY, period1);
		assertThatThrownBy(() -> timetableService.updatePeriod(period1, "Period 1", LocalTime.of(9, 0), LocalTime.of(9, 45), true))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> timetableService.deletePeriod(period1)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void teacherMustBeCurrentlyAssignedToTheSubjectForTheSection() {
		// bala teaches science to A only; maths in A is asha's.
		assertThatThrownBy(() -> timetableService.setEntry(sectionA, maths, bala, DayOfWeek.MONDAY, period1))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("not currently assigned");
		assertThatThrownBy(() -> timetableService.setEntry(sectionB, science, bala, DayOfWeek.MONDAY, period1))
				.isInstanceOf(IllegalStateException.class);
	}
}
