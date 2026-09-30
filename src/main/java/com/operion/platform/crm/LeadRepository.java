package com.operion.platform.crm;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LeadRepository extends JpaRepository<Lead, Long> {

	List<Lead> findAllByOrderByCreatedAtDesc();

	@Query("select l.stage, count(l) from Lead l group by l.stage")
	List<Object[]> countByStage();
}
