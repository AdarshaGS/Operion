package com.operion.finance;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface FeeDocumentCounterRepository extends JpaRepository<FeeDocumentCounter, Long> {

	/** Locked so two concurrent invoice/receipt generations in the same (year, type) never hand out the same number. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<FeeDocumentCounter> findByAcademicYearIdAndDocumentType(Long academicYearId, FeeDocumentType documentType);
}
