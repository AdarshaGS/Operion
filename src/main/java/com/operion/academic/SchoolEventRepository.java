package com.operion.academic;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolEventRepository extends JpaRepository<SchoolEvent, Long> {

	List<SchoolEvent> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);
}
