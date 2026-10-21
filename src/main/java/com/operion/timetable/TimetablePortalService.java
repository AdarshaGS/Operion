package com.operion.timetable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import com.operion.academic.Section;
import com.operion.authorization.AuthorizationDeniedException;
import com.operion.authorization.MembershipStatus;
import com.operion.authorization.OrganisationMembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a teacher or a guardian sees of the timetable. Only published sections are shown - a
 * draft stays an admin's working copy. Substitutions for the chosen week are overlaid so the
 * weekly pattern and the one-off cover are both visible.
 */
@Service
@Transactional(readOnly = true)
public class TimetablePortalService {

	/** weekStart is the Monday of the requested week; covers are the substitutions falling in Monday-Sunday of it. */
	public record View(LocalDate weekStart, LocalDate weekEnd, List<Period> periods, boolean published,
			List<TimetableEntry> entries, List<Cover> covers) {
	}

	/** role: COVERING (the caller is the substitute), COVERED (someone covers the caller's slot) or SUBSTITUTED (a guardian's child's slot has a stand-in). */
	public record Cover(String role, TimetableSubstitution substitution) {
	}

	private final OrganisationMembershipRepository membershipRepository;
	private final TimetableService timetableService;
	private final TimetableEntryRepository entryRepository;
	private final TimetablePublicationRepository publicationRepository;
	private final TimetableSubstitutionRepository substitutionRepository;

	public TimetablePortalService(OrganisationMembershipRepository membershipRepository, TimetableService timetableService,
			TimetableEntryRepository entryRepository, TimetablePublicationRepository publicationRepository,
			TimetableSubstitutionRepository substitutionRepository) {
		this.membershipRepository = membershipRepository;
		this.timetableService = timetableService;
		this.entryRepository = entryRepository;
		this.publicationRepository = publicationRepository;
		this.substitutionRepository = substitutionRepository;
	}

	/** The caller's own teaching week: their slots in published sections, plus cover they give or receive. */
	public View forTeacher(Long userId, LocalDate anyDayInWeek) {
		Long personId = membershipRepository.findByUserId(userId).stream().filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
				.findFirst().map(m -> m.getPerson().getId())
				.orElseThrow(() -> new AuthorizationDeniedException("No active membership for this user"));
		LocalDate weekStart = mondayOf(anyDayInWeek);
		LocalDate weekEnd = weekStart.plusDays(6);
		List<TimetableEntry> entries = entryRepository.findByTeacherPersonId(personId).stream()
				.filter(e -> !weekStart.isAfter(e.getAcademicYear().getEndDate()) && !weekEnd.isBefore(e.getAcademicYear().getStartDate()))
				.filter(e -> publicationRepository.findBySectionId(e.getSection().getId()).isPresent())
				.sorted(java.util.Comparator.comparing((TimetableEntry e) -> e.getDayOfWeek()).thenComparing(e -> e.getPeriod().getOrdinal()))
				.toList();
		List<Cover> covers = substitutionRepository.findByDateBetween(weekStart, weekEnd).stream()
				.flatMap(s -> {
					if (s.getSubstituteTeacher().getId().equals(personId)) {
						return java.util.stream.Stream.of(new Cover("COVERING", s));
					}
					if (s.getAbsentTeacher().getId().equals(personId)) {
						return java.util.stream.Stream.of(new Cover("COVERED", s));
					}
					return java.util.stream.Stream.<Cover>empty();
				})
				.sorted(java.util.Comparator.comparing((Cover c) -> c.substitution().getDate())
						.thenComparing(c -> c.substitution().getEntry().getPeriod().getStartTime()))
				.toList();
		return new View(weekStart, weekEnd, periodsUsedBy(entries), true, entries, covers);
	}

	/** A section's week for a guardian: nothing at all until an admin has published it. */
	public View forSection(Section section, LocalDate anyDayInWeek) {
		Long sectionId = section.getId();
		LocalDate weekStart = mondayOf(anyDayInWeek);
		LocalDate weekEnd = weekStart.plusDays(6);
		List<Period> periods = timetableService.periodsFor(section);
		if (publicationRepository.findBySectionId(sectionId).isEmpty()) {
			return new View(weekStart, weekEnd, periods, false, List.of(), List.of());
		}
		List<TimetableEntry> entries = entryRepository.findBySectionId(sectionId).stream()
				.sorted(java.util.Comparator.comparing((TimetableEntry e) -> e.getDayOfWeek()).thenComparing(e -> e.getPeriod().getOrdinal()))
				.toList();
		List<Cover> covers = substitutionRepository.findByDateBetween(weekStart, weekEnd).stream()
				.filter(s -> s.getEntry().getSection().getId().equals(sectionId))
				.map(s -> new Cover("SUBSTITUTED", s))
				.sorted(java.util.Comparator.comparing((Cover c) -> c.substitution().getDate())
						.thenComparing(c -> c.substitution().getEntry().getPeriod().getStartTime()))
				.toList();
		return new View(weekStart, weekEnd, periods, true, entries, covers);
	}

	/** The school day of every template the entries sit on (the default day when there are none), earliest first. */
	private List<Period> periodsUsedBy(List<TimetableEntry> entries) {
		java.util.Set<Long> seen = new java.util.LinkedHashSet<>();
		java.util.List<PeriodTemplate> days = new java.util.ArrayList<>();
		java.util.List<Period> periods = new java.util.ArrayList<>();
		boolean defaultDay = entries.isEmpty();
		for (TimetableEntry entry : entries) {
			PeriodTemplate template = entry.getPeriod().getTemplate();
			if (template == null) {
				defaultDay = true;
			} else if (seen.add(template.getId())) {
				days.add(template);
			}
		}
		if (defaultDay) {
			periods.addAll(timetableService.listPeriods());
		}
		days.forEach(template -> periods.addAll(timetableService.listPeriods(template)));
		periods.sort(java.util.Comparator.comparing(Period::getStartTime).thenComparing(Period::getOrdinal));
		return periods;
	}

	private static LocalDate mondayOf(LocalDate day) {
		return (day == null ? LocalDate.now() : day).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}
}
