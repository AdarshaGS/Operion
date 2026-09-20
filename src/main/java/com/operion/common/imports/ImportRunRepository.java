package com.operion.common.imports;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportRunRepository extends JpaRepository<ImportRun, Long> {

	List<ImportRun> findAllByOrderByCreatedAtDesc();
}
