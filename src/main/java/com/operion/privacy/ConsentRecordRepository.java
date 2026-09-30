package com.operion.privacy;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {

	List<ConsentRecord> findByPersonIdOrderByRecordedAtDescIdDesc(Long personId);
}
