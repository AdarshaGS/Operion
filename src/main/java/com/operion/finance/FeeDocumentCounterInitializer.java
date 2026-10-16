package com.operion.finance;

import com.operion.organisation.AcademicYear;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first counter row for an (academic year, document type) in its own short
 * transaction, committed before the caller takes the pessimistic lock on it. Doing the
 * lookup-then-insert inside the caller's transaction deadlocks under concurrent first use:
 * both transactions' SELECT ... FOR UPDATE on the missing row take a gap lock, then each
 * INSERT waits on the other's. Separate bean, not a FeeService method, so REQUIRES_NEW
 * actually goes through the proxy.
 */
@Component
public class FeeDocumentCounterInitializer {

	private final FeeDocumentCounterRepository feeDocumentCounterRepository;

	public FeeDocumentCounterInitializer(FeeDocumentCounterRepository feeDocumentCounterRepository) {
		this.feeDocumentCounterRepository = feeDocumentCounterRepository;
	}

	/** A concurrent creator winning the race surfaces as DataIntegrityViolationException - callers treat that as "it exists now". */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void create(AcademicYear academicYear, FeeDocumentType documentType) {
		feeDocumentCounterRepository.saveAndFlush(new FeeDocumentCounter(academicYear, documentType));
	}
}
