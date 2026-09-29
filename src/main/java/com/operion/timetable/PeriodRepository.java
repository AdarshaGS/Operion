package com.operion.timetable;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PeriodRepository extends JpaRepository<Period, Long> {

	List<Period> findAllByOrderByOrdinalAsc();

	boolean existsByOrdinal(int ordinal);
}
