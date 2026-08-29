package com.operion.student;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRequestRepository extends JpaRepository<TransferRequest, Long> {

	List<TransferRequest> findByStudentId(Long studentId);

	List<TransferRequest> findByStudentIdAndStatus(Long studentId, TransferRequestStatus status);

	List<TransferRequest> findByStatus(TransferRequestStatus status);
}
