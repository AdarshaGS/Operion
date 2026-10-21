package com.operion.timetable;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.operion.academic.ClassSubject;
import com.operion.academic.ClassSubjectRepository;
import com.operion.academic.ClassSubjectStatus;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.SchoolClassStatus;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.SectionStatus;
import com.operion.academic.TeacherAssignment;
import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentStatus;
import com.operion.academic.TeacherAssignmentType;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.organisation.OrganisationConfigurationRepository;
import com.operion.timetable.GenerationResult.SectionOutcome;
import com.operion.timetable.GenerationResult.Shortfall;
import org.springframework.stereotype.Service;

/**
 * Drafts a timetable from what the school has already configured: each class subject's weekly quota, the
 * subject teacher assigned to the section, the teaching periods, the organisation's working days, teacher
 * unavailability and the workload limits. It is a greedy fill, not an optimiser - subjects with the most
 * periods to place go first, and each period goes to the free slot that spreads the subject across the week
 * and balances the days. It places nothing it cannot justify and reports what is left over instead of
 * forcing it in. Every placement goes through {@link TimetableService#setEntry}, so the generator can never
 * break a rule the manual builder enforces.
 *
 * <p>Deliberately not transactional: a rejected candidate must roll back only its own write.
 */
@Service
public class TimetableGenerator {

	private static final int WORKING_DAYS_DEFAULT_MASK = 0b0111111;
	private static final int MAX_ATTEMPTS_PER_PERIOD = 60;

	private final TimetableService timetableService;
	private final TimetableEntryRepository entryRepository;
	private final ClassSubjectRepository classSubjectRepository;
	private final TeacherAssignmentRepository teacherAssignmentRepository;
	private final SectionRepository sectionRepository;
	private final SchoolClassRepository schoolClassRepository;
	private final OrganisationConfigurationRepository organisationConfigurationRepository;
	private final TeacherUnavailabilityRepository unavailabilityRepository;

	public TimetableGenerator(TimetableService timetableService, TimetableEntryRepository entryRepository,
			ClassSubjectRepository classSubjectRepository, TeacherAssignmentRepository teacherAssignmentRepository,
			SectionRepository sectionRepository, SchoolClassRepository schoolClassRepository,
			OrganisationConfigurationRepository organisationConfigurationRepository,
			TeacherUnavailabilityRepository unavailabilityRepository) {
		this.timetableService = timetableService;
		this.entryRepository = entryRepository;
		this.classSubjectRepository = classSubjectRepository;
		this.teacherAssignmentRepository = teacherAssignmentRepository;
		this.sectionRepository = sectionRepository;
		this.schoolClassRepository = schoolClassRepository;
		this.organisationConfigurationRepository = organisationConfigurationRepository;
		this.unavailabilityRepository = unavailabilityRepository;
	}

	public GenerationResult generateForSection(Section section, boolean clearExisting) {
		return summarise(List.of(generate(section, clearExisting)));
	}

	/** Every active section of every active class in the year, in class then section order. Published sections are skipped with a note. */
	public GenerationResult generateForYear(Long academicYearId, boolean clearExisting) {
		List<SectionOutcome> outcomes = new ArrayList<>();
		for (SchoolClass schoolClass : schoolClassRepository.findByAcademicYearId(academicYearId)) {
			if (schoolClass.getStatus() != SchoolClassStatus.ACTIVE) {
				continue;
			}
			for (Section section : sectionRepository.findBySchoolClassId(schoolClass.getId())) {
				if (section.getStatus() != SectionStatus.ACTIVE) {
					continue;
				}
				if (timetableService.isPublished(section.getId())) {
					outcomes.add(new SectionOutcome(section.getId(), section.getName(), className(section), 0,
							List.of(new Shortfall(null, "(whole section)", 0, "published - unpublish it before regenerating"))));
					continue;
				}
				outcomes.add(generate(section, clearExisting));
			}
		}
		return summarise(outcomes);
	}

	private SectionOutcome generate(Section section, boolean clearExisting) {
		if (timetableService.isPublished(section.getId())) {
			throw new IllegalStateException("The timetable is published - unpublish it before generating");
		}
		if (clearExisting) {
			timetableService.clearSection(section);
		}
		Long yearId = section.getSchoolClass().getAcademicYear().getId();
		List<Period> periods = timetableService.periodsFor(section).stream().filter(Period::isTeaching).toList();
		List<DayOfWeek> days = workingDays();
		TimetableSettings settings = timetableService.settings();

		Set<String> taken = new HashSet<>(); // "DAY|periodId" slots this section already fills
		Map<Long, Integer> placedBySubject = new HashMap<>();
		Map<DayOfWeek, Integer> sectionDayLoad = new HashMap<>();
		for (TimetableEntry entry : entryRepository.findBySectionId(section.getId())) {
			taken.add(key(entry.getDayOfWeek(), entry.getPeriod().getId()));
			placedBySubject.merge(entry.getSubject().getId(), 1, Integer::sum);
			sectionDayLoad.merge(entry.getDayOfWeek(), 1, Integer::sum);
		}

		// Teacher bookings across the whole year, kept current as this run places slots.
		Map<Long, List<TimetableEntry>> bookings = new HashMap<>();
		Map<Long, List<TeacherUnavailability>> blocks = new HashMap<>();

		List<Demand> demands = new ArrayList<>();
		List<Shortfall> shortfalls = new ArrayList<>();
		for (ClassSubject cs : classSubjectRepository.findBySchoolClassId(section.getSchoolClass().getId())) {
			if (cs.getStatus() != ClassSubjectStatus.ACTIVE || cs.getWeeklyPeriods() == null) {
				continue;
			}
			int need = cs.getWeeklyPeriods() - placedBySubject.getOrDefault(cs.getSubject().getId(), 0);
			if (need <= 0) {
				continue;
			}
			TeacherAssignment assignment = teacherAssignmentRepository
					.findBySectionIdAndSubjectIdAndAssignmentTypeAndStatus(section.getId(), cs.getSubject().getId(),
							TeacherAssignmentType.SUBJECT, TeacherAssignmentStatus.ACTIVE)
					.orElse(null);
			if (assignment == null) {
				shortfalls.add(new Shortfall(cs.getSubject().getId(), cs.getSubject().getName(), need, "no teacher is assigned to this subject for the section"));
				continue;
			}
			demands.add(new Demand(cs, assignment.getTeacherPerson(), need));
		}
		demands.sort(Comparator.comparingInt(Demand::need).reversed().thenComparing(d -> d.classSubject().getSubject().getName()));

		int placed = 0;
		for (Demand demand : demands) {
			Person teacher = demand.teacher();
			List<TimetableEntry> teacherBookings = bookings.computeIfAbsent(teacher.getId(),
					id -> new ArrayList<>(entryRepository.findByAcademicYearIdAndTeacherPersonId(yearId, id)));
			List<TeacherUnavailability> teacherBlocks = blocks.computeIfAbsent(teacher.getId(),
					id -> unavailabilityRepository.findByTeacherPersonId(id));
			Map<DayOfWeek, Integer> subjectDayCount = new HashMap<>();
			Map<Long, Integer> subjectPeriodCount = new HashMap<>();
			entryRepository.findBySectionId(section.getId()).stream()
					.filter(e -> e.getSubject().getId().equals(demand.classSubject().getSubject().getId()))
					.forEach(e -> {
						subjectDayCount.merge(e.getDayOfWeek(), 1, Integer::sum);
						subjectPeriodCount.merge(e.getPeriod().getId(), 1, Integer::sum);
					});

			int missing = 0;
			String lastReason = "no free slot where the teacher is available";
			for (int n = 0; n < demand.need(); n++) {
				List<Candidate> candidates = new ArrayList<>();
				for (int d = 0; d < days.size(); d++) {
					for (int p = 0; p < periods.size(); p++) {
						DayOfWeek day = days.get(d);
						Period period = periods.get(p);
						if (taken.contains(key(day, period.getId()))) {
							continue;
						}
						double score = subjectDayCount.getOrDefault(day, 0) * 100.0
								+ subjectPeriodCount.getOrDefault(period.getId(), 0) * 10.0
								+ sectionDayLoad.getOrDefault(day, 0) * 2.0
								+ teacherBookings.stream().filter(b -> b.getDayOfWeek() == day).count()
								+ d * 0.01 + p * 0.001;
						candidates.add(new Candidate(day, period, score));
					}
				}
				candidates.sort(Comparator.comparingDouble(Candidate::score));

				boolean done = false;
				int attempts = 0;
				for (Candidate c : candidates) {
					if (attempts >= MAX_ATTEMPTS_PER_PERIOD) {
						break;
					}
					if (!plausible(teacher, c, teacherBookings, teacherBlocks, settings)) {
						continue;
					}
					attempts++;
					try {
						TimetableEntry entry = timetableService.setEntry(section, demand.classSubject().getSubject(), teacher, c.day(), c.period());
						teacherBookings.add(entry);
						taken.add(key(c.day(), c.period().getId()));
						subjectDayCount.merge(c.day(), 1, Integer::sum);
						subjectPeriodCount.merge(c.period().getId(), 1, Integer::sum);
						sectionDayLoad.merge(c.day(), 1, Integer::sum);
						placed++;
						done = true;
						break;
					} catch (IllegalStateException rejected) {
						lastReason = rejected.getMessage();
					}
				}
				if (!done) {
					missing++;
				}
			}
			if (missing > 0) {
				shortfalls.add(new Shortfall(demand.classSubject().getSubject().getId(), demand.classSubject().getSubject().getName(), missing, lastReason));
			}
		}
		return new SectionOutcome(section.getId(), section.getName(), className(section), placed, shortfalls);
	}

	/** Cheap in-memory screen so most candidates that setEntry would reject are never sent to it. */
	private boolean plausible(Person teacher, Candidate c, List<TimetableEntry> bookings, List<TeacherUnavailability> blocks,
			TimetableSettings settings) {
		if (blocks.stream().anyMatch(b -> b.blocks(c.day(), c.period()))) {
			return false;
		}
		if (bookings.stream().anyMatch(b -> b.getDayOfWeek() == c.day()
				&& b.getPeriod().getStartTime().isBefore(c.period().getEndTime()) && b.getPeriod().getEndTime().isAfter(c.period().getStartTime()))) {
			return false;
		}
		Integer perDay = settings.getMaxTeacherPeriodsPerDay();
		if (perDay != null && bookings.stream().filter(b -> b.getDayOfWeek() == c.day()).count() >= perDay) {
			return false;
		}
		Integer perWeek = settings.getMaxTeacherPeriodsPerWeek();
		return perWeek == null || bookings.size() < perWeek;
	}

	private List<DayOfWeek> workingDays() {
		int mask = organisationConfigurationRepository.findById(TenantContext.getOrganisationId())
				.map(c -> c.getWorkingDaysMask()).orElse(WORKING_DAYS_DEFAULT_MASK);
		List<DayOfWeek> days = new ArrayList<>();
		for (DayOfWeek day : DayOfWeek.values()) { // MONDAY..SUNDAY, bit 0 = Monday
			if ((mask & (1 << day.ordinal())) != 0) {
				days.add(day);
			}
		}
		return days;
	}

	private static GenerationResult summarise(List<SectionOutcome> outcomes) {
		return new GenerationResult(outcomes.stream().mapToInt(SectionOutcome::placed).sum(),
				outcomes.stream().flatMap(o -> o.shortfalls().stream()).mapToInt(Shortfall::missing).sum(), outcomes);
	}

	private static String key(DayOfWeek day, Long periodId) {
		return day + "|" + periodId;
	}

	private static String className(Section section) {
		String displayName = section.getSchoolClass().getDisplayName();
		return displayName != null ? displayName : section.getSchoolClass().getGradeLevel().getName();
	}

	private record Demand(ClassSubject classSubject, Person teacher, int need) {
	}

	private record Candidate(DayOfWeek day, Period period, double score) {
	}
}
