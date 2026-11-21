package com.operion.timetable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

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
import com.operion.academic.WorkingCalendarEntry;
import com.operion.academic.WorkingCalendarEntryRepository;
import com.operion.academic.WorkingCalendarEntryType;
import com.operion.calendar.WorkingDayService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.hr.EmploymentType;
import com.operion.hr.LeaveRequest;
import com.operion.hr.LeaveRequestRepository;
import com.operion.hr.LeaveType;
import com.operion.hr.LeaveTypeRepository;
import com.operion.hr.StaffProfile;
import com.operion.hr.StaffProfileRepository;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Designation;
import com.operion.organisation.DesignationRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationConfiguration;
import com.operion.organisation.OrganisationConfigurationRepository;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Leave-to-cover: the working-calendar fix in needs(), the uncovered-slot report and the read-only cover suggestions. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, TimetableService.class, SubstitutionService.class, WorkingDayService.class,
		AcademicService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SubstitutionServiceTest {

	// 2025-06-02 is a Monday, inside the 2025-2026 academic year.
	private static final LocalDate MONDAY = LocalDate.of(2025, 6, 2);

	@Autowired private TimetableService timetableService;
	@Autowired private AcademicService academicService;
	@Autowired private SubstitutionService substitutionService;
	@Autowired private TimetableSubstitutionRepository substitutionRepository;
	@Autowired private OrganisationRepository organisationRepository;
	@Autowired private OrganisationConfigurationRepository configurationRepository;
	@Autowired private CampusRepository campusRepository;
	@Autowired private AcademicYearRepository academicYearRepository;
	@Autowired private PersonRepository personRepository;
	@Autowired private GradeLevelRepository gradeLevelRepository;
	@Autowired private SchoolClassRepository schoolClassRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private SubjectRepository subjectRepository;
	@Autowired private WorkingCalendarEntryRepository calendarEntryRepository;
	@Autowired private DesignationRepository designationRepository;
	@Autowired private StaffProfileRepository staffProfileRepository;
	@Autowired private LeaveTypeRepository leaveTypeRepository;
	@Autowired private LeaveRequestRepository leaveRequestRepository;

	/** One organisation's school: a campus, a class with sections A and B, two subjects, teachers, and the default school day. */
	private record School(AcademicYear year, Campus campus, SchoolClass schoolClass, Section sectionA, Section sectionB, Subject maths,
			Subject science, Person asha, Person bala, Period period1, Period period2, Designation designation, LeaveType leaveType) {
	}

	private School school;

	@BeforeEach
	void setUp() {
		school = buildSchool("sub-" + System.nanoTime());
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	/** Builds a school and leaves TenantContext on its organisation. */
	private School buildSchool(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);
		AcademicYear year = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(year, campus, grade, null));
		Section sectionA = sectionRepository.save(new Section(schoolClass, "A", 40, null));
		Section sectionB = sectionRepository.save(new Section(schoolClass, "B", 40, null));
		Subject maths = subjectRepository.save(new Subject("Maths", "MATH"));
		Subject science = subjectRepository.save(new Subject("Science", "SCI"));
		Person asha = personRepository.save(new Person("Asha", "Rao"));
		Person bala = personRepository.save(new Person("Bala", "Iyer"));
		LocalDate start = LocalDate.of(2025, 6, 1);
		academicService.assignTeacher(sectionA, maths, asha, TeacherAssignmentType.SUBJECT, start);
		academicService.assignTeacher(sectionB, maths, asha, TeacherAssignmentType.SUBJECT, start);
		academicService.assignTeacher(sectionA, science, bala, TeacherAssignmentType.SUBJECT, start);
		Period period1 = timetableService.createPeriod(1, "Period 1", LocalTime.of(9, 0), LocalTime.of(9, 45), false);
		Period period2 = timetableService.createPeriod(2, "Period 2", LocalTime.of(9, 45), LocalTime.of(10, 30), false);
		Designation designation = designationRepository.save(new Designation("Teacher"));
		LeaveType leaveType = leaveTypeRepository.save(new LeaveType("CASUAL", "Casual Leave", 12.0));
		return new School(year, campus, schoolClass, sectionA, sectionB, maths, science, asha, bala, period1, period2, designation, leaveType);
	}

	private LeaveRequest approvedLeave(School s, Person person, LocalDate from, LocalDate to) {
		LeaveRequest leave = pendingLeave(s, person, from, to);
		leave.approve(1L);
		return leaveRequestRepository.save(leave);
	}

	private LeaveRequest pendingLeave(School s, Person person, LocalDate from, LocalDate to) {
		StaffProfile staff = staffProfileRepository.findByPersonId(person.getId()).orElseGet(() -> staffProfileRepository.save(
				new StaffProfile(person, s.campus(), "EMP-" + person.getId(), s.designation(), null, LocalDate.of(2025, 6, 1), EmploymentType.PERMANENT)));
		return leaveRequestRepository.save(new LeaveRequest(staff, s.leaveType(), s.year(), from, to, 1, "test"));
	}

	private static List<Long> entryIds(List<SubstitutionService.LeaveSlot> slots) {
		return slots.stream().map(slot -> slot.need().entry().getId()).toList();
	}

	// ---- 1. needs() and the working calendar ----

	@Test
	void needsSkipsAHolidayAndAWeeklyOffButKeepsAnotherCampusHoliday() {
		TimetableEntry mon = timetableService.setEntry(school.sectionA(), school.maths(), school.asha(), DayOfWeek.MONDAY, school.period1());
		timetableService.setEntry(school.sectionA(), school.maths(), school.asha(), DayOfWeek.TUESDAY, school.period1());
		timetableService.setEntry(school.sectionA(), school.maths(), school.asha(), DayOfWeek.WEDNESDAY, school.period1());
		TimetableEntry thu = timetableService.setEntry(school.sectionA(), school.maths(), school.asha(), DayOfWeek.THURSDAY, school.period1());

		OrganisationConfiguration configuration = new OrganisationConfiguration(TenantContext.getOrganisationId(), "UTC");
		configuration.setWorkingDaysMask(0b0111101); // Tuesday (bit 1) is the weekly off
		configurationRepository.save(configuration);
		Campus otherCampus = campusRepository.save(new Campus("North Campus", "NORTH"));
		calendarEntryRepository.save(new WorkingCalendarEntry(school.year(), school.campus(), MONDAY.plusDays(2), "Founders day", WorkingCalendarEntryType.HOLIDAY));
		calendarEntryRepository.save(new WorkingCalendarEntry(school.year(), otherCampus, MONDAY.plusDays(3), "North only", WorkingCalendarEntryType.HOLIDAY));

		var needs = substitutionService.needs(school.asha().getId(), MONDAY, MONDAY.plusDays(3));

		// Tuesday is a weekly off, Wednesday a holiday on the section's campus; Thursday's holiday belongs to another campus.
		assertThat(needs).extracting(n -> n.entry().getId()).containsExactly(mon.getId(), thu.getId());
		assertThat(needs).extracting(SubstitutionService.Need::date).containsExactly(MONDAY, MONDAY.plusDays(3));
	}

	@Test
	void anOrgWideHolidayRemovesTheSlotForEveryCampus() {
		timetableService.setEntry(school.sectionA(), school.maths(), school.asha(), DayOfWeek.MONDAY, school.period1());
		calendarEntryRepository.save(new WorkingCalendarEntry(school.year(), null, MONDAY, "Republic day", WorkingCalendarEntryType.HOLIDAY));

		assertThat(substitutionService.needs(school.asha().getId(), MONDAY, MONDAY)).isEmpty();
		assertThat(substitutionService.needs(school.asha().getId(), MONDAY, MONDAY.plusDays(7))).hasSize(1);
	}

	// ---- 2. uncovered() ----

	@Test
	void uncoveredListsOnlyApprovedLeave() {
		Person chitra = personRepository.save(new Person("Chitra", "Nair"));
		academicService.assignTeacher(school.sectionB(), school.science(), chitra, TeacherAssignmentType.SUBJECT, LocalDate.of(2025, 6, 1));
		TimetableEntry ashaSlot = timetableService.setEntry(school.sectionA(), school.maths(), school.asha(), DayOfWeek.MONDAY, school.period1());
		timetableService.setEntry(school.sectionA(), school.science(), school.bala(), DayOfWeek.MONDAY, school.period2());
		timetableService.setEntry(school.sectionB(), school.science(), chitra, DayOfWeek.MONDAY, school.period1());
		LeaveRequest approved = approvedLeave(school, school.asha(), MONDAY, MONDAY);
		pendingLeave(school, school.bala(), MONDAY, MONDAY);
		LeaveRequest rejected = pendingLeave(school, chitra, MONDAY, MONDAY);
		rejected.reject(1L);
		leaveRequestRepository.save(rejected);

		var slots = substitutionService.uncovered(MONDAY, MONDAY, null, false);

		assertThat(slots).singleElement().satisfies(slot -> {
			assertThat(slot.need().entry().getId()).isEqualTo(ashaSlot.getId());
			assertThat(slot.leave().getId()).isEqualTo(approved.getId());
			assertThat(slot.leave().getStaffProfile().getPerson().getId()).isEqualTo(school.asha().getId());
			assertThat(slot.campusId()).isEqualTo(school.campus().getId());
		});
	}

	@Test
	void uncoveredOnlyDropsSlotsThatAlreadyHaveCover() {
		TimetableEntry covered = timetableService.setEntry(school.sectionA(), school.maths(), school.asha(), DayOfWeek.MONDAY, school.period1());
		TimetableEntry open = timetableService.setEntry(school.sectionB(), school.maths(), school.asha(), DayOfWeek.MONDAY, school.period2());
		approvedLeave(school, school.asha(), MONDAY, MONDAY);
		substitutionService.assign(covered, MONDAY, school.bala(), "fever");

		var all = substitutionService.uncovered(MONDAY, MONDAY, null, false);
		assertThat(entryIds(all)).containsExactly(covered.getId(), open.getId());
		assertThat(all.get(0).need().substitution()).isNotNull();
		assertThat(all.get(1).need().substitution()).isNull();

		assertThat(entryIds(substitutionService.uncovered(MONDAY, MONDAY, null, true))).containsExactly(open.getId());
	}

	@Test
	void uncoveredClampsEachLeaveToTheRequestedRange() {
		timetableService.setEntry(school.sectionA(), school.maths(), school.asha(), DayOfWeek.MONDAY, school.period1());
		// A month-long leave: only the Mondays inside the asked-about week are reported.
		approvedLeave(school, school.asha(), LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30));

		var slots = substitutionService.uncovered(LocalDate.of(2025, 6, 9), LocalDate.of(2025, 6, 15), null, false);
		assertThat(slots).extracting(s -> s.need().date()).containsExactly(LocalDate.of(2025, 6, 9));

		// A leave that begins inside the range and ends after it, and one that ended before it.
		assertThat(substitutionService.uncovered(LocalDate.of(2025, 6, 23), LocalDate.of(2025, 7, 6), null, false))
				.extracting(s -> s.need().date()).containsExactly(LocalDate.of(2025, 6, 23), LocalDate.of(2025, 6, 30));
		assertThat(substitutionService.uncovered(LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 14), null, false)).isEmpty();
	}

	@Test
	void uncoveredRejectsARangeOverSixtyTwoDaysEvenWhenTheLeaveIsShort() {
		timetableService.setEntry(school.sectionA(), school.maths(), school.asha(), DayOfWeek.MONDAY, school.period1());
		approvedLeave(school, school.asha(), MONDAY, MONDAY);

		assertThatThrownBy(() -> substitutionService.uncovered(MONDAY, MONDAY.plusDays(63), null, false))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("62 days");
		assertThat(substitutionService.uncovered(MONDAY, MONDAY.plusDays(62), null, false)).hasSize(1);
		assertThatThrownBy(() -> substitutionService.uncovered(MONDAY, MONDAY.minusDays(1), null, false))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void uncoveredFiltersBySectionCampusAndIsEmptyWithoutTimetableEntries() {
		assertThat(substitutionService.uncovered(MONDAY, MONDAY, null, false)).isEmpty();
		approvedLeave(school, school.asha(), MONDAY, MONDAY);
		assertThat(substitutionService.uncovered(MONDAY, MONDAY, null, false)).isEmpty(); // leave but no slots

		Campus north = campusRepository.save(new Campus("North Campus", "NORTH"));
		SchoolClass northClass = schoolClassRepository.save(new SchoolClass(school.year(), north,
				gradeLevelRepository.save(new GradeLevel("Grade 6", 6, null)), null));
		Section northSection = sectionRepository.save(new Section(northClass, "A", 30, null));
		academicService.assignTeacher(northSection, school.maths(), school.bala(), TeacherAssignmentType.SUBJECT, LocalDate.of(2025, 6, 1));
		TimetableEntry mainSlot = timetableService.setEntry(school.sectionA(), school.maths(), school.asha(), DayOfWeek.MONDAY, school.period1());
		TimetableEntry northSlot = timetableService.setEntry(northSection, school.maths(), school.bala(), DayOfWeek.MONDAY, school.period2());
		approvedLeave(school, school.bala(), MONDAY, MONDAY);

		assertThat(entryIds(substitutionService.uncovered(MONDAY, MONDAY, null, false))).containsExactly(mainSlot.getId(), northSlot.getId());
		assertThat(entryIds(substitutionService.uncovered(MONDAY, MONDAY, school.campus().getId(), false))).containsExactly(mainSlot.getId());
		assertThat(substitutionService.uncovered(MONDAY, MONDAY, north.getId(), false)).singleElement().satisfies(slot -> {
			assertThat(slot.need().entry().getId()).isEqualTo(northSlot.getId());
			assertThat(slot.campusId()).isEqualTo(north.getId());
		});
	}

	@Test
	void anotherOrganisationsLeaveNeverShowsUp() {
		timetableService.setEntry(school.sectionA(), school.maths(), school.asha(), DayOfWeek.MONDAY, school.period1());
		approvedLeave(school, school.asha(), MONDAY, MONDAY);
		Long orgA = TenantContext.getOrganisationId();

		School other = buildSchool("sub-other-" + System.nanoTime());
		TimetableEntry otherSlot = timetableService.setEntry(other.sectionA(), other.maths(), other.asha(), DayOfWeek.MONDAY, other.period1());
		approvedLeave(other, other.asha(), MONDAY, MONDAY);
		Long orgB = TenantContext.getOrganisationId();

		TenantContext.set(orgA, null);
		var seenByA = substitutionService.uncovered(MONDAY, MONDAY, null, false);
		assertThat(seenByA).hasSize(1);
		assertThat(entryIds(seenByA)).doesNotContain(otherSlot.getId());
		assertThat(substitutionService.suggest(MONDAY, MONDAY, null)).hasSize(1);

		TenantContext.set(orgB, null);
		assertThat(entryIds(substitutionService.uncovered(MONDAY, MONDAY, null, false))).containsExactly(otherSlot.getId());
	}

	// ---- 3. suggest() ----

	@Test
	void suggestNeverProposesOneTeacherForOverlappingPeriodsAndLeavesNullWhenNobodyIsLeft() {
		// A shorter school day for another class: 09:15-09:45 overlaps Period 1 (09:00-09:45) without being the same period.
		PeriodTemplate primary = timetableService.createTemplate("Primary");
		Period short1 = timetableService.createPeriod(primary, 1, "P1", LocalTime.of(9, 15), LocalTime.of(9, 45), SlotType.TEACHING);
		SchoolClass youngClass = schoolClassRepository.save(new SchoolClass(school.year(), school.campus(),
				gradeLevelRepository.save(new GradeLevel("Grade 1", 1, null)), null));
		Section young = sectionRepository.save(new Section(youngClass, "A", 30, null));
		timetableService.assignTemplate(youngClass, primary);
		Person dev = personRepository.save(new Person("Dev", "Menon"));
		academicService.assignTeacher(young, school.science(), dev, TeacherAssignmentType.SUBJECT, LocalDate.of(2025, 6, 1));

		TimetableEntry ashaFirst = timetableService.setEntry(school.sectionA(), school.maths(), school.asha(), DayOfWeek.MONDAY, school.period1());
		TimetableEntry devShort = timetableService.setEntry(young, school.science(), dev, DayOfWeek.MONDAY, short1);
		TimetableEntry ashaSecond = timetableService.setEntry(school.sectionB(), school.maths(), school.asha(), DayOfWeek.MONDAY, school.period2());
		approvedLeave(school, school.asha(), MONDAY, MONDAY);
		approvedLeave(school, dev, MONDAY, MONDAY);
		long before = substitutionRepository.count();

		var suggestions = substitutionService.suggest(MONDAY, MONDAY, null);

		// Only Bala is free. Slots in time order: Period 1 (09:00), the short P1 (09:15, overlaps it), Period 2 (09:45, does not).
		assertThat(suggestions).extracting(s -> s.slot().need().entry().getId())
				.containsExactly(ashaFirst.getId(), devShort.getId(), ashaSecond.getId());
		assertThat(suggestions.get(0).proposed().person().getId()).isEqualTo(school.bala().getId());
		assertThat(suggestions.get(0).reasonIfNone()).isNull();
		assertThat(suggestions.get(1).proposed()).isNull();
		assertThat(suggestions.get(1).reasonIfNone()).isNotBlank();
		assertThat(suggestions.get(2).proposed().person().getId()).isEqualTo(school.bala().getId());
		assertThat(substitutionRepository.count()).isEqualTo(before);
	}

	@Test
	void suggestPrefersTheLighterLoadIncludingProposalsAlreadyMade() {
		Person zoya = personRepository.save(new Person("Zoya", "Zed"));
		academicService.assignTeacher(school.sectionB(), school.science(), zoya, TeacherAssignmentType.SUBJECT, LocalDate.of(2025, 6, 1));
		timetableService.setEntry(school.sectionA(), school.maths(), school.asha(), DayOfWeek.MONDAY, school.period1());
		timetableService.setEntry(school.sectionB(), school.maths(), school.asha(), DayOfWeek.MONDAY, school.period2());
		approvedLeave(school, school.asha(), MONDAY, MONDAY);

		var suggestions = substitutionService.suggest(MONDAY, MONDAY, null);

		// Period 1 is section A's, so Bala (teaches it) takes it. Period 2 is section B's: Bala and Zoya both only "Free this period",
		// Bala sorts first by name, but already holds a proposal, so Zoya (lighter) gets it.
		assertThat(suggestions).hasSize(2);
		assertThat(suggestions.get(0).proposed().person().getId()).isEqualTo(school.bala().getId());
		assertThat(suggestions.get(1).proposed().person().getId()).isEqualTo(zoya.getId());
	}

	@Test
	void suggestWritesNothingAndSkipsSlotsThatAreAlreadyCovered() {
		TimetableEntry covered = timetableService.setEntry(school.sectionA(), school.maths(), school.asha(), DayOfWeek.MONDAY, school.period1());
		TimetableEntry open = timetableService.setEntry(school.sectionB(), school.maths(), school.asha(), DayOfWeek.MONDAY, school.period2());
		approvedLeave(school, school.asha(), MONDAY, MONDAY);
		substitutionService.assign(covered, MONDAY, school.bala(), null);
		long before = substitutionRepository.count();

		var suggestions = substitutionService.suggest(MONDAY, MONDAY.plusDays(6), null);

		assertThat(suggestions).extracting(s -> s.slot().need().entry().getId()).containsExactly(open.getId());
		// Bala covers Period 1 already, which ends as Period 2 starts, so he is still free for the open slot.
		assertThat(suggestions.get(0).proposed().person().getId()).isEqualTo(school.bala().getId());
		assertThat(substitutionRepository.count()).isEqualTo(before);
	}

	@Test
	void suggestRejectsARangeOverFourteenDays() {
		assertThatThrownBy(() -> substitutionService.suggest(MONDAY, MONDAY.plusDays(15), null))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("14 days");
		assertThat(substitutionService.suggest(MONDAY, MONDAY.plusDays(14), null)).isEmpty();
		assertThatThrownBy(() -> substitutionService.suggest(MONDAY, MONDAY.minusDays(1), null)).isInstanceOf(IllegalArgumentException.class);
	}
}
