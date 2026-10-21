package com.operion.timetable;

import java.time.LocalTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Periods are grouped by school day: the default day has no template, and each PeriodTemplate is its own day. */
public interface PeriodRepository extends JpaRepository<Period, Long> {

	List<Period> findAllByOrderByOrdinalAsc();

	List<Period> findByTemplateIsNullOrderByOrdinalAsc();

	List<Period> findByTemplateIdOrderByOrdinalAsc(Long templateId);

	boolean existsByTemplateIsNullAndOrdinal(int ordinal);

	boolean existsByTemplateIdAndOrdinal(Long templateId, int ordinal);

	boolean existsByTemplateId(Long templateId);

	/** True when some period of the default day intersects [start, end) - back-to-back periods (one ends where the next starts) do not. */
	boolean existsByTemplateIsNullAndStartTimeLessThanAndEndTimeGreaterThan(LocalTime end, LocalTime start);

	boolean existsByTemplateIdAndStartTimeLessThanAndEndTimeGreaterThan(Long templateId, LocalTime end, LocalTime start);

	boolean existsByTemplateIsNullAndIdNotAndStartTimeLessThanAndEndTimeGreaterThan(Long id, LocalTime end, LocalTime start);

	boolean existsByTemplateIdAndIdNotAndStartTimeLessThanAndEndTimeGreaterThan(Long templateId, Long id, LocalTime end, LocalTime start);
}
