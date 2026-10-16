package com.operion.student;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicantRepository extends JpaRepository<Applicant, Long> {

	Page<Applicant> findByStatus(ApplicantStatus status, Pageable pageable);

	boolean existsByPersonIdAndStatusIn(Long personId, java.util.Collection<ApplicantStatus> statuses);

	long countByStatus(ApplicantStatus status);
}
