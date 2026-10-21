package com.operion.timetable;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TimetableEntryRepository extends JpaRepository<TimetableEntry, Long> {

	List<TimetableEntry> findBySectionId(Long sectionId);

	List<TimetableEntry> findByTeacherPersonId(Long teacherPersonId);

	Optional<TimetableEntry> findBySectionIdAndDayOfWeekAndPeriodId(Long sectionId, DayOfWeek dayOfWeek, Long periodId);

	Optional<TimetableEntry> findByAcademicYearIdAndTeacherPersonIdAndDayOfWeekAndPeriodId(
			Long academicYearId, Long teacherPersonId, DayOfWeek dayOfWeek, Long periodId);

	/** Every slot a teacher holds in one academic year - the basis for the time-overlap and workload rules. */
	List<TimetableEntry> findByAcademicYearIdAndTeacherPersonId(Long academicYearId, Long teacherPersonId);

	List<TimetableEntry> findByAcademicYearIdAndRoomId(Long academicYearId, Long roomId);

	boolean existsByRoomId(Long roomId);

	boolean existsByPeriodId(Long periodId);

	boolean existsBySectionSchoolClassId(Long schoolClassId);

	List<TimetableEntry> findByAcademicYearId(Long academicYearId);
}
