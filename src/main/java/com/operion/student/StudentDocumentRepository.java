package com.operion.student;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentDocumentRepository extends JpaRepository<StudentDocument, Long> {

	List<StudentDocument> findByStudentId(Long studentId);

	Optional<StudentDocument> findByStudentIdAndDocumentTypeAndStatus(
			Long studentId, String documentType, StudentDocumentStatus status);
}
