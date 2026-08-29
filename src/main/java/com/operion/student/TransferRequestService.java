package com.operion.student;

import com.operion.organisation.Campus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Split out of StudentService (rather than folded in, unlike HrService/LeaveRequest)
 * to avoid an eighth constructor parameter rippling through the many tests that build
 * StudentService directly with positional args - same precedent as StudentImportService
 * already being its own class alongside StudentService.
 */
@Service
public class TransferRequestService {

	private final TransferRequestRepository transferRequestRepository;

	public TransferRequestService(TransferRequestRepository transferRequestRepository) {
		this.transferRequestRepository = transferRequestRepository;
	}

	/** Intra-org transfer only - both campuses belong to this same organisation. Does not
	 * itself move the student's enrollment; see TransferRequest's class-level note. */
	public TransferRequest raise(Student student, Campus fromCampus, Campus toCampus, String reason, Long requestedBy) {
		return transferRequestRepository.save(new TransferRequest(student, fromCampus, toCampus, reason, requestedBy));
	}

	@Transactional
	public TransferRequest approve(TransferRequest transferRequest, Long decidedBy) {
		transferRequest.approve(decidedBy);
		return transferRequestRepository.save(transferRequest);
	}

	public TransferRequest reject(TransferRequest transferRequest, Long decidedBy) {
		transferRequest.reject(decidedBy);
		return transferRequestRepository.save(transferRequest);
	}
}
