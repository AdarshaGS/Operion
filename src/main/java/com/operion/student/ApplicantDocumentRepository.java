package com.operion.student;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicantDocumentRepository extends JpaRepository<ApplicantDocument, Long> {

	List<ApplicantDocument> findByApplicantId(Long applicantId);
}
