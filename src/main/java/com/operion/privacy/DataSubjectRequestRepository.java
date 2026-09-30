package com.operion.privacy;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DataSubjectRequestRepository extends JpaRepository<DataSubjectRequest, Long> {

	List<DataSubjectRequest> findByStatusOrderByDueDate(DataSubjectRequestStatus status);

	List<DataSubjectRequest> findByPersonIdOrderByIdDesc(Long personId);

	List<DataSubjectRequest> findAllByOrderByIdDesc();
}
