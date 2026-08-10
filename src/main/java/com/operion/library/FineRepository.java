package com.operion.library;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FineRepository extends JpaRepository<Fine, Long> {

	List<Fine> findByBorrowRecordId(Long borrowRecordId);

	List<Fine> findByStatus(FineStatus status);
}
