package com.operion.academic;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkingCalendarEntryRepository extends JpaRepository<WorkingCalendarEntry, Long> {

	List<WorkingCalendarEntry> findByAcademicYearIdOrderByDateAsc(Long academicYearId);
}
