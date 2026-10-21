package com.operion.timetable;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PeriodTemplateRepository extends JpaRepository<PeriodTemplate, Long> {

	List<PeriodTemplate> findAllByOrderByNameAsc();

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
