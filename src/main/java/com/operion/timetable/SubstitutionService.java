package com.operion.timetable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.operion.academic.TeacherAssignment;
import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentStatus;
import com.operion.hr.LeaveRequestRepository;
import com.operion.hr.LeaveRequestStatus;
import com.operion.hr.StaffProfileRepository;
import com.operion.identity.Person;
import com.operion.organisation.AcademicYear;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-day cover for timetable slots. Finds the slots an absent teacher would miss in a date range, ranks the
 * colleagues who could take each one, and records who does. A candidate must be a teacher of the same academic
 * year who is free in that period (no slot of their own, no cover already given), is not marked unavailable
 * and is not on approved leave that day. Cover never edits the weekly timetable.
 */
@Service
public class SubstitutionService {

	static final int MAX_RANGE_DAYS = 62;

	private final TimetableEntryRepository entryRepository;
	private final TimetableSubstitutionRepository substitutionRepository;
	private final TeacherAssignmentRepository teacherAssignmentRepository;
	private final TeacherUnavailabilityRepository unavailabilityRepository;
	private final StaffProfileRepository staffProfileRepository;
	private final LeaveRequestRepository leaveRequestRepository;

	public SubstitutionService(TimetableEntryRepository entryRepository, TimetableSubstitutionRepository substitutionRepository,
			TeacherAssignmentRepository teacherAssignmentRepository, TeacherUnavailabilityRepository unavailabilityRepository,
			StaffProfileRepository staffProfileRepository, LeaveRequestRepository leaveRequestRepository) {
		this.entryRepository = entryRepository;
		this.substitutionRepository = substitutionRepository;
		this.teacherAssignmentRepository = teacherAssignmentRepository;
		this.unavailabilityRepository = unavailabilityRepository;
		this.staffProfileRepository = staffProfileRepository;
		this.leaveRequestRepository = leaveRequestRepository;
	}

	/** A slot the absent teacher would miss on {@code date}, with its cover (if any) and who could take it. */
	public record Need(LocalDate date, TimetableEntry entry, TimetableSubstitution substitution, List<Candidate> candidates) {
	}

	public record Candidate(Person person, String rank) {
	}

	public List<Need> needs(Long absentTeacherPersonId, LocalDate from, LocalDate to) {
		if (to.isBefore(from)) {
			throw new IllegalArgumentException("The end date is before the start date");
		}
		if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
			throw new IllegalArgumentException("Look at no more than " + MAX_RANGE_DAYS + " days at a time");
		}
		List<TimetableEntry> mine = entryRepository.findByTeacherPersonId(absentTeacherPersonId);
		List<Need> needs = new ArrayList<>();
		for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
			for (TimetableEntry entry : mine) {
				if (entry.getDayOfWeek() != date.getDayOfWeek() || !coversDate(entry.getAcademicYear(), date)) {
					continue;
				}
				TimetableSubstitution cover = substitutionRepository.findByEntryIdAndDate(entry.getId(), date).orElse(null);
				needs.add(new Need(date, entry, cover, cover == null ? candidates(entry, date) : List.of()));
			}
		}
		needs.sort(Comparator.comparing(Need::date).thenComparing(n -> n.entry().getPeriod().getStartTime()));
		return needs;
	}

	/** Free colleagues for the slot on that date, best first: teaches this section, then teaches this subject elsewhere, then anyone free; lighter day first. */
	public List<Candidate> candidates(TimetableEntry entry, LocalDate date) {
		AcademicYear year = entry.getAcademicYear();
		Long absentId = entry.getTeacherPerson().getId();
		List<TeacherAssignment> assignments = teacherAssignmentRepository.findByAcademicYearIdAndStatus(year.getId(), TeacherAssignmentStatus.ACTIVE);
		Map<Long, Person> pool = new LinkedHashMap<>();
		assignments.forEach(a -> pool.putIfAbsent(a.getTeacherPerson().getId(), a.getTeacherPerson()));
		pool.remove(absentId);

		List<Candidate> free = new ArrayList<>();
		Map<Long, Integer> load = new LinkedHashMap<>();
		for (Person person : pool.values()) {
			if (!isFree(person, entry, date)) {
				continue;
			}
			boolean sameSection = assignments.stream().anyMatch(a -> a.getTeacherPerson().getId().equals(person.getId())
					&& a.getSection() != null && a.getSection().getId().equals(entry.getSection().getId()));
			boolean sameSubject = assignments.stream().anyMatch(a -> a.getTeacherPerson().getId().equals(person.getId())
					&& a.getSubject() != null && a.getSubject().getId().equals(entry.getSubject().getId()));
			free.add(new Candidate(person, sameSection ? "Teaches this section" : sameSubject ? "Teaches " + entry.getSubject().getName() : "Free this period"));
			load.put(person.getId(), dayLoad(person, entry, date));
		}
		free.sort(Comparator.comparingInt((Candidate c) -> rankOrder(c.rank())).thenComparingInt(c -> load.get(c.person().getId()))
				.thenComparing(c -> c.person().getLastName() + c.person().getFirstName()));
		return free;
	}

	@Transactional
	public TimetableSubstitution assign(TimetableEntry entry, LocalDate date, Person substitute, String reason) {
		if (entry.getDayOfWeek() != date.getDayOfWeek()) {
			throw new IllegalStateException("The slot is on " + entry.getDayOfWeek() + ", but " + date + " is a " + date.getDayOfWeek());
		}
		if (!coversDate(entry.getAcademicYear(), date)) {
			throw new IllegalStateException("The date is outside the slot's academic year");
		}
		if (substitute.getId().equals(entry.getTeacherPerson().getId())) {
			throw new IllegalStateException("The substitute is the slot's own teacher");
		}
		if (!isFree(substitute, entry, date)) {
			throw new IllegalStateException(substitute.getFirstName() + " " + substitute.getLastName() + " is not free for "
					+ entry.getPeriod().getLabel() + " on " + date + " (already teaching, covering, unavailable or on leave)");
		}
		return substitutionRepository.findByEntryIdAndDate(entry.getId(), date)
				.map(existing -> {
					existing.changeSubstitute(substitute, reason);
					return substitutionRepository.save(existing);
				})
				.orElseGet(() -> substitutionRepository.save(new TimetableSubstitution(entry, date, entry.getTeacherPerson(), substitute, reason)));
	}

	@Transactional
	public void remove(TimetableSubstitution substitution) {
		substitutionRepository.delete(substitution);
	}

	public List<TimetableSubstitution> onDate(LocalDate date) {
		return substitutionRepository.findByDate(date);
	}

	/** Absence range for an approved or pending leave request's staff member - the same lookup the leave screens use. */
	public boolean onApprovedLeave(Person person, LocalDate date) {
		return staffProfileRepository.findByPersonId(person.getId())
				.map(profile -> leaveRequestRepository.existsByStaffProfileIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
						profile.getId(), LeaveRequestStatus.APPROVED, date, date))
				.orElse(false);
	}

	private boolean isFree(Person person, TimetableEntry entry, LocalDate date) {
		DayOfWeek day = entry.getDayOfWeek();
		Period period = entry.getPeriod();
		boolean teaching = entryRepository.findByAcademicYearIdAndTeacherPersonId(entry.getAcademicYear().getId(), person.getId()).stream()
				.anyMatch(own -> own.getDayOfWeek() == day && overlaps(own.getPeriod(), period)
						// their own slot doesn't count if someone else is already covering it that day
						&& substitutionRepository.findByEntryIdAndDate(own.getId(), date).isEmpty());
		if (teaching) {
			return false;
		}
		boolean covering = substitutionRepository.findBySubstituteTeacherIdAndDate(person.getId(), date).stream()
				.anyMatch(other -> !other.getEntry().getId().equals(entry.getId()) && overlaps(other.getEntry().getPeriod(), period));
		if (covering) {
			return false;
		}
		if (unavailabilityRepository.findByTeacherPersonId(person.getId()).stream().anyMatch(block -> block.blocks(day, period))) {
			return false;
		}
		return !onApprovedLeave(person, date);
	}

	private int dayLoad(Person person, TimetableEntry entry, LocalDate date) {
		long own = entryRepository.findByAcademicYearIdAndTeacherPersonId(entry.getAcademicYear().getId(), person.getId()).stream()
				.filter(e -> e.getDayOfWeek() == entry.getDayOfWeek()).count();
		return (int) own + substitutionRepository.findBySubstituteTeacherIdAndDate(person.getId(), date).size();
	}

	private static int rankOrder(String rank) {
		return rank.startsWith("Teaches this section") ? 0 : rank.startsWith("Teaches ") ? 1 : 2;
	}

	private static boolean coversDate(AcademicYear year, LocalDate date) {
		return !date.isBefore(year.getStartDate()) && !date.isAfter(year.getEndDate());
	}

	private static boolean overlaps(Period a, Period b) {
		return a.getStartTime().isBefore(b.getEndTime()) && a.getEndTime().isAfter(b.getStartTime());
	}
}
