package com.operion.hr;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffDocumentRepository extends JpaRepository<StaffDocument, Long> {

	List<StaffDocument> findByStaffProfileIdAndStatus(Long staffProfileId, StaffDocumentStatus status);

	Optional<StaffDocument> findByStaffProfileIdAndDocumentTypeAndStatus(Long staffProfileId, String documentType, StaffDocumentStatus status);
}
