package com.operion.timetable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import com.operion.common.InvalidRequestException;
import com.operion.academic.AcademicService;
import com.operion.academic.ClassSubject;
import com.operion.academic.ClassSubjectRepository;
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
@Import({ MultiTenancyConfig.class, JpaConfig.class, TimetableService.class, TimetableGenerator.class, SubstitutionService.class, AcademicService.class, TimetablePortalService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TimetableServiceTest {

	@Autowired private TimetableService timetableService;
	@Autowired private AcademicService academicService;
	@Autowired private TimetableEntryRepository entryRepository;
	@Autowired private PeriodRepository periodRepository;
	@Autowired private TimetableSettingsRepository settingsRepository;
	@Autowired private OrganisationRepository organisationRepository;
	@Autowired private CampusRepository campusRepository;
	@Autowired private AcademicYearRepository academicYearRepository;
	@Autowired private PersonRepository personRepository;
	@Autowired private GradeLevelRepository gradeLevelRepository;
	@Autowired private SchoolClassRepository schoolClassRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private SubjectRepository subjectRepository;
	@Autowired private ClassSubjectRepository classSubjectRepository;
	@Autowired private TimetableGenerator generator;
	@Autowired private SubstitutionService substitutionService;
	@Autowired private TimetablePortalService portalService;

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
				.isInstanceOf(InvalidRequestException.class);
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

	private void saveSettings(int gapMinutes, Integer perDay, Integer perWeek) {
		TimetableSettings settings = settingsRepository.findByOrganisationId(TenantContext.getOrganisationId())
				.orElseGet(TimetableSettings::new);
		settings.setGapMinutes(gapMinutes);
		settings.setMaxTeacherPeriodsPerDay(perDay);
		settings.setMaxTeacherPeriodsPerWeek(perWeek);
		settingsRepository.save(settings);
	}

	@Test
	void minimumGapAppliesBetweenTeachingPeriodsButNotNextToABreak() {
		saveSettings(10, null, null);

		// Right after the lunch break: exempt from the gap.
		Period period3 = timetableService.createPeriod(4, "Period 3", LocalTime.of(11, 0), LocalTime.of(11, 45), false);
		// Right after a teaching period: needs 10 minutes.
		assertThatThrownBy(() -> timetableService.createPeriod(5, "Period 4", LocalTime.of(11, 50), LocalTime.of(12, 30), false))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("at least 10 minutes after Period 3");
		timetableService.createPeriod(5, "Period 4", LocalTime.of(11, 55), LocalTime.of(12, 30), false);
		// A period inserted so that the next teaching period follows too closely is rejected as well.
		assertThatThrownBy(() -> timetableService.createPeriod(6, "Period 3b", LocalTime.of(11, 45), LocalTime.of(11, 50), false))
				.isInstanceOf(IllegalStateException.class);
		// Editing is judged the same way, and a period never compares against itself.
		assertThatThrownBy(() -> timetableService.updatePeriod(period3, "Period 3", LocalTime.of(11, 0), LocalTime.of(11, 50), false))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("Period 4 must start at least 10 minutes");
		timetableService.updatePeriod(period3, "Period 3 (renamed)", LocalTime.of(11, 0), LocalTime.of(11, 45), false);
	}

	@Test
	void periodsThatAlreadyExistAreNeverReCheckedWhenTheGapIsRaised() {
		saveSettings(15, null, null);

		// period1 and period2 are back to back from setUp; renaming one (same times) must still work.
		timetableService.updatePeriod(period2, "Period Two", LocalTime.of(9, 45), LocalTime.of(10, 30), false);
		// Moving its times is judged against the new gap, even if it stays back to back.
		assertThatThrownBy(() -> timetableService.updatePeriod(period2, "Period Two", LocalTime.of(9, 45), LocalTime.of(10, 20), false))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("at least 15 minutes after Period 1");
		timetableService.updatePeriod(period2, "Period Two", LocalTime.of(10, 0), LocalTime.of(10, 30), false);
	}

	@Test
	void aSpecialSlotNeverHoldsEntriesAndIsExemptFromTheGap() {
		saveSettings(10, null, null);

		Period assembly = timetableService.createPeriod(7, "Assembly", LocalTime.of(7, 30), LocalTime.of(8, 0), SlotType.SPECIAL);
		assertThat(assembly.getSlotType()).isEqualTo(SlotType.SPECIAL);
		assertThat(assembly.isBreakSlot()).isTrue();
		timetableService.createPeriod(8, "Period 0", LocalTime.of(8, 0), LocalTime.of(8, 45), false);

		assertThatThrownBy(() -> timetableService.setEntry(sectionA, maths, asha, DayOfWeek.MONDAY, assembly))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("special");

		// Once a teaching period is used it cannot be turned into a special slot either.
		timetableService.setEntry(sectionA, maths, asha, DayOfWeek.MONDAY, period1);
		assertThatThrownBy(() -> timetableService.updatePeriod(period1, "Period 1", LocalTime.of(9, 0), LocalTime.of(9, 45), SlotType.SPECIAL))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("special slot");
	}

	@Test
	void teacherWorkloadLimitsApplyPerDayAndPerWeek() {
		saveSettings(0, 1, 2);

		timetableService.setEntry(sectionA, maths, asha, DayOfWeek.MONDAY, period1);
		// Second period the same day: over the daily limit of 1.
		assertThatThrownBy(() -> timetableService.setEntry(sectionB, maths, asha, DayOfWeek.MONDAY, period2))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("limit of 1 period a day");
		// Replacing the slot's own entry is not a second period.
		timetableService.setEntry(sectionA, maths, asha, DayOfWeek.MONDAY, period1);

		timetableService.setEntry(sectionB, maths, asha, DayOfWeek.TUESDAY, period1);
		// Third period in the week: over the weekly limit of 2.
		assertThatThrownBy(() -> timetableService.setEntry(sectionB, maths, asha, DayOfWeek.WEDNESDAY, period1))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("limit of 2 periods a week");
		assertThat(entryRepository.findBySectionId(sectionB.getId())).hasSize(1);

		// Clearing the limit lifts the rule.
		saveSettings(0, null, null);
		timetableService.setEntry(sectionB, maths, asha, DayOfWeek.WEDNESDAY, period1);
	}

	@Test
	void aSubjectWithAWeeklyQuotaCannotBePlacedMoreOftenThanThatAndCoverageShowsTheShortfall() {
		ClassSubject classMaths = classSubjectRepository.save(new ClassSubject(sectionA.getSchoolClass(), maths, true));
		classMaths.updateWeeklyPeriods(2);
		classSubjectRepository.save(classMaths);

		timetableService.setEntry(sectionA, maths, asha, DayOfWeek.MONDAY, period1);
		assertThat(timetableService.coverage(sectionA)).singleElement().satisfies(line -> {
			assertThat(line.requiredPerWeek()).isEqualTo(2);
			assertThat(line.placed()).isEqualTo(1);
			assertThat(line.remaining()).isEqualTo(1);
		});

		timetableService.setEntry(sectionA, maths, asha, DayOfWeek.TUESDAY, period1);
		assertThatThrownBy(() -> timetableService.setEntry(sectionA, maths, asha, DayOfWeek.WEDNESDAY, period1))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("already has its 2 weekly periods");

		// Re-saving a slot with the same subject is not an extra period; another subject without a quota is unaffected.
		timetableService.setEntry(sectionA, maths, asha, DayOfWeek.TUESDAY, period1);
		timetableService.setEntry(sectionA, science, bala, DayOfWeek.WEDNESDAY, period1);
		assertThat(timetableService.coverage(sectionA)).extracting(SubjectCoverage::subjectName, SubjectCoverage::placed, SubjectCoverage::remaining)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("Maths", 2L, 0L), org.assertj.core.groups.Tuple.tuple("Science", 1L, 0L));

		// Clearing the quota lifts the cap.
		classMaths.updateWeeklyPeriods(0);
		classSubjectRepository.save(classMaths);
		timetableService.setEntry(sectionA, maths, asha, DayOfWeek.THURSDAY, period1);
	}

	@Test
	void aRoomCannotHostTwoSectionsAtOnceAndAnInactiveOrUsedRoomIsProtected() {
		Room lab = timetableService.createRoom("Science Lab", "Lab", 30);
		assertThatThrownBy(() -> timetableService.createRoom("science lab", null, null))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("already exists");

		timetableService.setEntry(sectionA, science, bala, DayOfWeek.MONDAY, period1, lab);
		academicService.assignTeacher(sectionB, science, bala, TeacherAssignmentType.SUBJECT, LocalDate.of(2025, 6, 1));
		// Different teacher not needed: the same room in the same period is the clash.
		academicService.assignTeacher(sectionB, maths, asha, TeacherAssignmentType.SUBJECT, LocalDate.of(2025, 6, 2));
		assertThatThrownBy(() -> timetableService.setEntry(sectionB, maths, asha, DayOfWeek.MONDAY, period1, lab))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("Room Science Lab is already booked");
		// Another day, or no room, is fine; replacing the slot's own entry keeps its room without clashing.
		timetableService.setEntry(sectionB, maths, asha, DayOfWeek.TUESDAY, period1, lab);
		timetableService.setEntry(sectionA, science, bala, DayOfWeek.MONDAY, period1, lab);
		assertThat(entryRepository.findBySectionIdAndDayOfWeekAndPeriodId(sectionA.getId(), DayOfWeek.MONDAY, period1.getId()))
				.get().satisfies(entry -> assertThat(entry.getRoom().getId()).isEqualTo(lab.getId()));
		// Saving without a room clears it.
		timetableService.setEntry(sectionA, science, bala, DayOfWeek.MONDAY, period1);
		assertThat(entryRepository.findBySectionIdAndDayOfWeekAndPeriodId(sectionA.getId(), DayOfWeek.MONDAY, period1.getId()))
				.get().satisfies(entry -> assertThat(entry.getRoom()).isNull());

		// A room with slots can't be deleted, only deactivated; an inactive room can't take new slots.
		assertThatThrownBy(() -> timetableService.deleteRoom(lab)).isInstanceOf(IllegalStateException.class).hasMessageContaining("inactive");
		timetableService.updateRoom(lab, "Science Lab", "Lab", 30, false);
		assertThatThrownBy(() -> timetableService.setEntry(sectionA, science, bala, DayOfWeek.WEDNESDAY, period1, lab))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("inactive");
		Room spare = timetableService.createRoom("Spare", null, null);
		timetableService.deleteRoom(spare);
	}

	@Test
	void aTimetableIsADraftUntilPublishedAndAnEmptyOneCannotBePublished() {
		assertThat(timetableService.isPublished(sectionA.getId())).isFalse();
		assertThatThrownBy(() -> timetableService.publish(sectionA)).isInstanceOf(IllegalStateException.class).hasMessageContaining("nothing to publish");

		timetableService.setEntry(sectionA, maths, asha, DayOfWeek.MONDAY, period1);
		timetableService.publish(sectionA);
		timetableService.publish(sectionA); // idempotent
		assertThat(timetableService.isPublished(sectionA.getId())).isTrue();
		assertThat(timetableService.publication(sectionA.getId())).get().satisfies(p -> assertThat(p.getCreatedAt()).isNotNull());

		timetableService.unpublish(sectionA);
		assertThat(timetableService.isPublished(sectionA.getId())).isFalse();

		// Year-wide publish only touches sections that have slots and are not published yet.
		timetableService.setEntry(sectionB, maths, asha, DayOfWeek.MONDAY, period2);
		assertThat(timetableService.publishYear(sectionA.getSchoolClass().getAcademicYear().getId())).isEqualTo(2);
		assertThat(timetableService.publishYear(sectionA.getSchoolClass().getAcademicYear().getId())).isZero();
	}

	private void quota(Subject subject, int weekly) {
		ClassSubject cs = classSubjectRepository.save(new ClassSubject(sectionA.getSchoolClass(), subject, true));
		cs.updateWeeklyPeriods(weekly);
		classSubjectRepository.save(cs);
	}

	@Test
	void generatorFillsQuotasSpreadsSubjectsAcrossDaysAndNeverDoubleBooksATeacher() {
		quota(maths, 3);
		quota(science, 2);

		GenerationResult result = generator.generateForYear(sectionA.getSchoolClass().getAcademicYear().getId(), false);

		// A has maths (asha) + science (bala); B has only maths (asha) - 3 + 2 + 3 placed. Quotas belong to the class,
		// so B also owes science, but nobody teaches it there: reported, not forced.
		assertThat(result.placed()).isEqualTo(8);
		assertThat(result.unplaced()).isEqualTo(2);
		assertThat(result.sections().stream().flatMap(o -> o.shortfalls().stream()))
				.singleElement().satisfies(s -> {
					assertThat(s.subjectName()).isEqualTo("Science");
					assertThat(s.reason()).contains("no teacher is assigned");
				});
		assertThat(timetableService.coverage(sectionA)).extracting(SubjectCoverage::subjectName, SubjectCoverage::placed)
				.containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("Maths", 3L), org.assertj.core.groups.Tuple.tuple("Science", 2L));
		// Maths lands on three different days in section A.
		assertThat(entryRepository.findBySectionId(sectionA.getId()).stream().filter(e -> e.getSubject().getId().equals(maths.getId()))
				.map(TimetableEntry::getDayOfWeek).distinct()).hasSize(3);
		// asha teaches both sections but never twice in one slot.
		var ashaSlots = entryRepository.findByTeacherPersonId(asha.getId()).stream()
				.map(e -> e.getDayOfWeek() + "|" + e.getPeriod().getId()).toList();
		assertThat(ashaSlots).hasSize(6).doesNotHaveDuplicates();
		// Running it again places nothing new (quotas are met).
		assertThat(generator.generateForYear(sectionA.getSchoolClass().getAcademicYear().getId(), false).placed()).isZero();
	}

	@Test
	void generatorRespectsUnavailabilityWorkloadAndMissingTeachersAndReportsTheShortfall() {
		quota(maths, 4);
		Subject art = subjectRepository.save(new Subject("Art", "ART"));
		quota(art, 2); // nobody teaches art in section A

		// asha is out on Monday and Tuesday entirely, and capped at 2 periods a week.
		timetableService.addUnavailability(asha, DayOfWeek.MONDAY, null, "part-time");
		timetableService.addUnavailability(asha, DayOfWeek.TUESDAY, null, null);
		saveSettings(0, null, 2);

		GenerationResult result = generator.generateForSection(sectionA, false);

		assertThat(result.placed()).isEqualTo(2);
		assertThat(entryRepository.findBySectionId(sectionA.getId())).extracting(TimetableEntry::getDayOfWeek)
				.doesNotContain(DayOfWeek.MONDAY, DayOfWeek.TUESDAY);
		assertThat(result.sections().get(0).shortfalls()).extracting(GenerationResult.Shortfall::subjectName, GenerationResult.Shortfall::missing)
				.containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("Maths", 2), org.assertj.core.groups.Tuple.tuple("Art", 2));
		assertThat(result.sections().get(0).shortfalls()).filteredOn(s -> s.subjectName().equals("Art"))
				.singleElement().satisfies(s -> assertThat(s.reason()).contains("no teacher is assigned"));

		// The manual builder refuses an unavailable slot with the reason.
		saveSettings(0, null, null);
		assertThatThrownBy(() -> timetableService.setEntry(sectionB, maths, asha, DayOfWeek.MONDAY, period2))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("unavailable on MONDAY (part-time)");
	}

	@Test
	void generatorRefusesAPublishedTimetableAndClearExistingStartsOver() {
		quota(maths, 2);
		generator.generateForSection(sectionA, false);
		timetableService.publish(sectionA);

		assertThatThrownBy(() -> generator.generateForSection(sectionA, false)).isInstanceOf(IllegalStateException.class).hasMessageContaining("published");

		timetableService.unpublish(sectionA);
		timetableService.setEntry(sectionA, science, bala, DayOfWeek.SATURDAY, period2);
		generator.generateForSection(sectionA, true);
		// Cleared first, so the hand-placed science slot is gone and only the quota'd maths remains.
		assertThat(entryRepository.findBySectionId(sectionA.getId())).hasSize(2).allSatisfy(e -> assertThat(e.getSubject().getId()).isEqualTo(maths.getId()));
	}

	@Test
	void substituteCoversASlotForOneDateOnlyWhenFreeAndLeavesTheWeeklyTimetableUntouched() {
		LocalDate monday = LocalDate.of(2025, 6, 2);
		TimetableEntry slot = timetableService.setEntry(sectionA, maths, asha, DayOfWeek.MONDAY, period1);

		var needs = substitutionService.needs(asha.getId(), monday, monday.plusDays(6));
		assertThat(needs).singleElement().satisfies(need -> {
			assertThat(need.date()).isEqualTo(monday);
			assertThat(need.substitution()).isNull();
			// bala teaches science to section A, so is the best (and only) free colleague.
			assertThat(need.candidates()).singleElement().satisfies(c -> {
				assertThat(c.person().getId()).isEqualTo(bala.getId());
				assertThat(c.rank()).isEqualTo("Teaches this section");
			});
		});

		// A date on the wrong weekday is refused; the right one is recorded.
		assertThatThrownBy(() -> substitutionService.assign(slot, monday.plusDays(1), bala, null))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("is on MONDAY");
		assertThatThrownBy(() -> substitutionService.assign(slot, monday, asha, null))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("own teacher");
		substitutionService.assign(slot, monday, bala, "fever");
		assertThat(substitutionService.onDate(monday)).singleElement().satisfies(sub -> {
			assertThat(sub.getSubstituteTeacher().getId()).isEqualTo(bala.getId());
			assertThat(sub.getAbsentTeacher().getId()).isEqualTo(asha.getId());
			assertThat(sub.getReason()).isEqualTo("fever");
		});
		assertThat(substitutionService.needs(asha.getId(), monday, monday)).singleElement()
				.satisfies(need -> assertThat(need.substitution()).isNotNull());
		// The weekly slot still belongs to asha, and the next Monday is uncovered.
		assertThat(entryRepository.findById(slot.getId())).get().satisfies(e -> assertThat(e.getTeacherPerson().getId()).isEqualTo(asha.getId()));
		assertThat(substitutionService.onDate(monday.plusDays(7))).isEmpty();

		// Removing the slot removes its cover.
		timetableService.removeEntry(slot);
		assertThat(substitutionService.onDate(monday)).isEmpty();
	}

	@Test
	void aTeacherWhoIsTeachingCoveringOrUnavailableIsNotOfferedAndCannotBeAssigned() {
		LocalDate monday = LocalDate.of(2025, 6, 2);
		TimetableEntry ashaSlot = timetableService.setEntry(sectionB, maths, asha, DayOfWeek.MONDAY, period2);
		timetableService.setEntry(sectionA, science, bala, DayOfWeek.MONDAY, period2); // bala is teaching then

		assertThat(substitutionService.candidates(ashaSlot, monday)).isEmpty();
		assertThatThrownBy(() -> substitutionService.assign(ashaSlot, monday, bala, null))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("is not free");

		// Unavailable in period 1 means no cover there either.
		TimetableEntry ashaFirst = timetableService.setEntry(sectionB, maths, asha, DayOfWeek.MONDAY, period1);
		assertThat(substitutionService.candidates(ashaFirst, monday)).hasSize(1);
		timetableService.addUnavailability(bala, DayOfWeek.MONDAY, period1, "meeting");
		assertThat(substitutionService.candidates(ashaFirst, monday)).isEmpty();

		// A date outside the academic year is refused.
		assertThatThrownBy(() -> substitutionService.assign(ashaFirst, LocalDate.of(2026, 6, 1), bala, null))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("outside");
	}

	@Test
	void aGuardianSeesASectionsWeekOnlyOncePublishedWithThatWeeksCoverOverlaid() {
		LocalDate monday = LocalDate.of(2025, 6, 2);
		TimetableEntry slot = timetableService.setEntry(sectionA, maths, asha, DayOfWeek.MONDAY, period1);
		substitutionService.assign(slot, monday, bala, "fever");

		// A draft shows the school day but none of the slots, and no cover.
		var draft = portalService.forSection(sectionA, monday.plusDays(2));
		assertThat(draft.published()).isFalse();
		assertThat(draft.entries()).isEmpty();
		assertThat(draft.covers()).isEmpty();
		assertThat(draft.periods()).hasSize(3);

		timetableService.publish(sectionA);
		var week = portalService.forSection(sectionA, monday.plusDays(2));
		assertThat(week.weekStart()).isEqualTo(monday);
		assertThat(week.weekEnd()).isEqualTo(monday.plusDays(6));
		assertThat(week.entries()).singleElement().satisfies(e -> assertThat(e.getId()).isEqualTo(slot.getId()));
		assertThat(week.covers()).singleElement().satisfies(c -> {
			assertThat(c.role()).isEqualTo("SUBSTITUTED");
			assertThat(c.substitution().getSubstituteTeacher().getId()).isEqualTo(bala.getId());
		});

		// The following week has the same slot but no cover.
		var next = portalService.forSection(sectionA, monday.plusDays(7));
		assertThat(next.entries()).hasSize(1);
		assertThat(next.covers()).isEmpty();
	}

	@Test
	void aClassOnAPeriodTemplateUsesOnlyThatDaysPeriodsAndTeacherClashesStillFollowTheClock() {
		PeriodTemplate primary = timetableService.createTemplate("Primary");
		assertThatThrownBy(() -> timetableService.createTemplate("primary")).isInstanceOf(IllegalStateException.class);
		// The same ordinal and overlapping clock times are fine in another day; within it they are not.
		Period short1 = timetableService.createPeriod(primary, 1, "P1", LocalTime.of(9, 15), LocalTime.of(9, 45), SlotType.TEACHING);
		timetableService.createPeriod(primary, 2, "P2", LocalTime.of(9, 45), LocalTime.of(10, 15), SlotType.TEACHING);
		assertThatThrownBy(() -> timetableService.createPeriod(primary, 1, "Dup", LocalTime.of(12, 0), LocalTime.of(12, 30), SlotType.TEACHING))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("ordinal");
		assertThatThrownBy(() -> timetableService.createPeriod(primary, 5, "Clash", LocalTime.of(10, 0), LocalTime.of(10, 30), SlotType.TEACHING))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("overlap");
		assertThat(timetableService.listPeriods()).extracting(Period::getLabel).containsExactly("Period 1", "Period 2", "Lunch");
		assertThat(timetableService.listPeriods(primary)).extracting(Period::getLabel).containsExactly("P1", "P2");

		// Section B's class follows Primary (sectionA and B share one class, so give the class the template).
		timetableService.assignTemplate(sectionB.getSchoolClass(), primary);
		assertThat(timetableService.periodsFor(sectionB)).extracting(Period::getLabel).containsExactly("P1", "P2");
		assertThatThrownBy(() -> timetableService.setEntry(sectionB, maths, asha, DayOfWeek.MONDAY, period1))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("not part of the school day");
		timetableService.setEntry(sectionB, maths, asha, DayOfWeek.MONDAY, short1);

		// Once it has entries the class cannot change day; clearing them frees it again.
		assertThatThrownBy(() -> timetableService.assignTemplate(sectionB.getSchoolClass(), null))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("already has timetable entries");
		assertThatThrownBy(() -> timetableService.deleteTemplate(primary)).isInstanceOf(IllegalStateException.class);
		timetableService.clearSection(sectionB);
		timetableService.assignTemplate(sectionB.getSchoolClass(), null);
		assertThat(timetableService.templateOf(sectionB.getSchoolClass())).isEmpty();
		assertThatThrownBy(() -> timetableService.setEntry(sectionB, maths, asha, DayOfWeek.MONDAY, short1))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("not part of the school day");
	}

	@Test
	void aTeacherCannotBeBookedInTwoDaysPeriodsThatOverlapOnTheClock() {
		PeriodTemplate primary = timetableService.createTemplate("Primary");
		Period short1 = timetableService.createPeriod(primary, 1, "P1", LocalTime.of(9, 15), LocalTime.of(9, 45), SlotType.TEACHING);
		SchoolClass otherClass = schoolClassRepository.save(new SchoolClass(sectionA.getSchoolClass().getAcademicYear(),
				sectionA.getSchoolClass().getCampus(), gradeLevelRepository.save(new GradeLevel("Grade 1", 1, null)), null));
		Section young = sectionRepository.save(new Section(otherClass, "A", 30, null));
		academicService.assignTeacher(young, maths, asha, TeacherAssignmentType.SUBJECT, LocalDate.of(2025, 6, 1));
		timetableService.assignTemplate(otherClass, primary);

		timetableService.setEntry(sectionA, maths, asha, DayOfWeek.MONDAY, period1); // 09:00-09:45 on the default day
		assertThatThrownBy(() -> timetableService.setEntry(young, maths, asha, DayOfWeek.MONDAY, short1)) // 09:15-09:45 on Primary
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("already booked");
		timetableService.setEntry(young, maths, asha, DayOfWeek.TUESDAY, short1);

		// Deleting a template needs it unused and empty.
		assertThatThrownBy(() -> timetableService.deleteTemplate(primary)).isInstanceOf(IllegalStateException.class).hasMessageContaining("used by a class");
		timetableService.clearSection(young);
		timetableService.assignTemplate(otherClass, null);
		assertThatThrownBy(() -> timetableService.deleteTemplate(primary)).isInstanceOf(IllegalStateException.class).hasMessageContaining("still has periods");
		timetableService.deletePeriod(short1);
		timetableService.deleteTemplate(primary);
		assertThat(timetableService.listTemplates()).isEmpty();
	}
}
