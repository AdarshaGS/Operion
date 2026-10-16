package com.operion.timetable;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import com.operion.academic.Section;
import com.operion.academic.Subject;
import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentStatus;
import com.operion.identity.Person;
import com.operion.organisation.AcademicYear;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimetableService {

	private final PeriodRepository periodRepository;
	private final TimetableEntryRepository entryRepository;
	private final TeacherAssignmentRepository teacherAssignmentRepository;

	public TimetableService(PeriodRepository periodRepository, TimetableEntryRepository entryRepository,
			TeacherAssignmentRepository teacherAssignmentRepository) {
		this.periodRepository = periodRepository;
		this.entryRepository = entryRepository;
		this.teacherAssignmentRepository = teacherAssignmentRepository;
	}

	public List<Period> listPeriods() {
		return periodRepository.findAllByOrderByOrdinalAsc();
	}

	@Transactional
	public Period createPeriod(int ordinal, String label, LocalTime startTime, LocalTime endTime, boolean breakSlot) {
		if (periodRepository.existsByOrdinal(ordinal)) {
			throw new IllegalStateException("A period with ordinal " + ordinal + " already exists");
		}
		Period period = new Period(ordinal, label, startTime, endTime, breakSlot);
		if (periodRepository.existsByStartTimeLessThanAndEndTimeGreaterThan(endTime, startTime)) {
			throw new IllegalStateException("The period's times overlap an existing period");
		}
		return periodRepository.save(period);
	}

	/** A slot that already holds entries can't become a break - it would strand them. */
	@Transactional
	public Period updatePeriod(Period period, String label, LocalTime startTime, LocalTime endTime, boolean breakSlot) {
		if (breakSlot && entryRepository.existsByPeriodId(period.getId())) {
			throw new IllegalStateException("Period " + period.getLabel() + " has timetable entries and cannot become a break");
		}
		period.update(label, startTime, endTime, breakSlot);
		if (periodRepository.existsByIdNotAndStartTimeLessThanAndEndTimeGreaterThan(period.getId(), endTime, startTime)) {
			throw new IllegalStateException("The period's times overlap an existing period");
		}
		return periodRepository.save(period);
	}

	@Transactional
	public void deletePeriod(Period period) {
		if (entryRepository.existsByPeriodId(period.getId())) {
			throw new IllegalStateException("Period " + period.getLabel() + " has timetable entries - remove them first");
		}
		periodRepository.delete(period);
	}

	/**
	 * Places (or replaces) the entry for a section's (day, period) slot. Rejects break
	 * periods, a teacher who isn't currently assigned to teach the subject to the section,
	 * and a teacher already booked elsewhere in the same year/day/period. Replacing the
	 * slot's own existing entry is not a clash with itself.
	 */
	@Transactional
	public TimetableEntry setEntry(Section section, Subject subject, Person teacher, DayOfWeek day, Period period) {
		if (period.isBreakSlot()) {
			throw new IllegalStateException("Cannot schedule a subject in a break period (" + period.getLabel() + ")");
		}
		if (!teacherAssignmentRepository.existsBySectionIdAndSubjectIdAndTeacherPersonIdAndStatus(
				section.getId(), subject.getId(), teacher.getId(), TeacherAssignmentStatus.ACTIVE)) {
			throw new IllegalStateException("Teacher is not currently assigned to teach " + subject.getName() + " to this section");
		}

		AcademicYear academicYear = section.getSchoolClass().getAcademicYear();
		Optional<TimetableEntry> existing =
				entryRepository.findBySectionIdAndDayOfWeekAndPeriodId(section.getId(), day, period.getId());

		entryRepository.findByAcademicYearIdAndTeacherPersonIdAndDayOfWeekAndPeriodId(
				academicYear.getId(), teacher.getId(), day, period.getId())
				.filter(clash -> existing.isEmpty() || !clash.getId().equals(existing.get().getId()))
				.ifPresent(clash -> {
					throw new IllegalStateException("Teacher is already booked on " + day + " " + period.getLabel()
							+ " (" + className(clash.getSection()) + " "
							+ clash.getSection().getName() + ")");
				});

		if (existing.isPresent()) {
			existing.get().reassign(subject, teacher);
			return entryRepository.save(existing.get());
		}
		return entryRepository.save(new TimetableEntry(academicYear, section, subject, teacher, day, period));
	}

	@Transactional
	public void removeEntry(TimetableEntry entry) {
		entryRepository.delete(entry);
	}

	private static String className(Section section) {
		String displayName = section.getSchoolClass().getDisplayName();
		return displayName != null ? displayName : section.getSchoolClass().getGradeLevel().getName();
	}
}
