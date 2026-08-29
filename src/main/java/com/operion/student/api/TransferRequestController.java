package com.operion.student.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.common.TenantContext;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.student.Student;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentRepository;
import com.operion.student.TransferRequest;
import com.operion.student.TransferRequestRepository;
import com.operion.student.TransferRequestService;
import com.operion.student.TransferRequestStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Raise is nested under /students/{id} (fromCampus is derived, not caller-supplied -
 * see raise()); decide is top-level to match LeaveRequestController's shape. decidedBy
 * is always the caller's own actor id from TenantContext, never a request body field -
 * an improvement over LeaveRequestController's decidedBy-in-body precedent, which
 * forced the frontend to hardcode a placeholder id.
 */
@RestController
@RequirePermission("STUDENT_VIEW")
public class TransferRequestController {

	private final TransferRequestService transferRequestService;
	private final StudentRepository studentRepository;
	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final TransferRequestRepository transferRequestRepository;
	private final CampusRepository campusRepository;

	public TransferRequestController(TransferRequestService transferRequestService, StudentRepository studentRepository,
			StudentEnrollmentRepository studentEnrollmentRepository, TransferRequestRepository transferRequestRepository,
			CampusRepository campusRepository) {
		this.transferRequestService = transferRequestService;
		this.studentRepository = studentRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.transferRequestRepository = transferRequestRepository;
		this.campusRepository = campusRepository;
	}

	@PostMapping("/api/v1/students/{studentId}/transfer-requests")
	@RequirePermission("STUDENT_TRANSFER_MANAGE")
	public TransferRequestResponse raise(@PathVariable Long studentId, @RequestBody RaiseTransferRequestRequest request) {
		Student student = findStudent(studentId);
		StudentEnrollment currentEnrollment = studentEnrollmentRepository.findByStudentIdAndCurrentTrue(studentId)
				.orElseThrow(() -> new IllegalStateException("Student " + studentId + " has no current enrollment to transfer from"));
		Campus fromCampus = currentEnrollment.getSection().getSchoolClass().getCampus();
		Campus toCampus = findCampus(request.toCampusId());
		TransferRequest transferRequest = transferRequestService.raise(
				student, fromCampus, toCampus, request.reason(), TenantContext.getActorId());
		return TransferRequestResponse.from(transferRequest);
	}

	/** studentId scopes to one student's history; status alone (no studentId) is an
	 * org-wide inbox, e.g. all pending requests awaiting a decision. At least one must be given. */
	@GetMapping("/api/v1/transfer-requests")
	public List<TransferRequestResponse> list(@RequestParam(required = false) Long studentId, @RequestParam(required = false) String status) {
		TransferRequestStatus parsedStatus = status != null ? TransferRequestStatus.valueOf(status) : null;
		List<TransferRequest> requests;
		if (studentId != null && parsedStatus != null) {
			requests = transferRequestRepository.findByStudentIdAndStatus(studentId, parsedStatus);
		} else if (studentId != null) {
			requests = transferRequestRepository.findByStudentId(studentId);
		} else if (parsedStatus != null) {
			requests = transferRequestRepository.findByStatus(parsedStatus);
		} else {
			throw new IllegalArgumentException("studentId or status must be provided");
		}
		return requests.stream().map(TransferRequestResponse::from).toList();
	}

	@PostMapping("/api/v1/transfer-requests/{id}/approve")
	@RequirePermission("STUDENT_TRANSFER_MANAGE")
	public TransferRequestResponse approve(@PathVariable Long id) {
		return TransferRequestResponse.from(transferRequestService.approve(findTransferRequest(id), TenantContext.getActorId()));
	}

	@PostMapping("/api/v1/transfer-requests/{id}/reject")
	@RequirePermission("STUDENT_TRANSFER_MANAGE")
	public TransferRequestResponse reject(@PathVariable Long id) {
		return TransferRequestResponse.from(transferRequestService.reject(findTransferRequest(id), TenantContext.getActorId()));
	}

	private Student findStudent(Long id) {
		return studentRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No student with id " + id));
	}

	private Campus findCampus(Long id) {
		return campusRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No campus with id " + id));
	}

	private TransferRequest findTransferRequest(Long id) {
		return transferRequestRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No transfer request with id " + id));
	}
}
