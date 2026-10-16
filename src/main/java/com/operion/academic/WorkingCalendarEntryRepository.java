package com.operion.academic;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkingCalendarEntryRepository extends JpaRepository<WorkingCalendarEntry, Long> {

	List<WorkingCalendarEntry> findByAcademicYearIdOrderByDateAsc(Long academicYearId);

	boolean existsByAcademicYearIdAndDateAndCampusId(Long academicYearId, LocalDate date, Long campusId);

	boolean existsByAcademicYearIdAndDateAndCampusIsNull(Long academicYearId, LocalDate date);

	boolean existsByAcademicYearIdAndDateAndTypeAndCampusIsNull(
			Long academicYearId, LocalDate date, WorkingCalendarEntryType type);

	boolean existsByAcademicYearIdAndDateAndTypeAndCampusId(
			Long academicYearId, LocalDate date, WorkingCalendarEntryType type, Long campusId);
}
