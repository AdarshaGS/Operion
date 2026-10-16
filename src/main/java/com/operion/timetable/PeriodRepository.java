package com.operion.timetable;

import java.time.LocalTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PeriodRepository extends JpaRepository<Period, Long> {

	List<Period> findAllByOrderByOrdinalAsc();

	boolean existsByOrdinal(int ordinal);

	/** True when some period's time range intersects [start, end) - back-to-back periods (one ends where the next starts) do not. */
	boolean existsByStartTimeLessThanAndEndTimeGreaterThan(LocalTime end, LocalTime start);

	boolean existsByIdNotAndStartTimeLessThanAndEndTimeGreaterThan(Long id, LocalTime end, LocalTime start);
}
