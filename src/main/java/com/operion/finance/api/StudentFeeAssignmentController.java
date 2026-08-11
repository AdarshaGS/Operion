package com.operion.finance.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.finance.FeeService;
import com.operion.finance.FeeStructure;
import com.operion.finance.FeeStructureInstallment;
import com.operion.finance.FeeStructureInstallmentRepository;
import com.operion.finance.FeeStructureRepository;
import com.operion.finance.Invoice;
import com.operion.finance.StudentFeeAssignment;
import com.operion.finance.StudentFeeAssignmentRepository;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fees/assignments")
@RequirePermission("FEE_VIEW")
public class StudentFeeAssignmentController {

	private final FeeService feeService;
	private final StudentFeeAssignmentRepository studentFeeAssignmentRepository;
	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final FeeStructureRepository feeStructureRepository;
	private final FeeStructureInstallmentRepository feeStructureInstallmentRepository;

	public StudentFeeAssignmentController(FeeService feeService, StudentFeeAssignmentRepository studentFeeAssignmentRepository,
			StudentEnrollmentRepository studentEnrollmentRepository, FeeStructureRepository feeStructureRepository,
			FeeStructureInstallmentRepository feeStructureInstallmentRepository) {
		this.feeService = feeService;
		this.studentFeeAssignmentRepository = studentFeeAssignmentRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.feeStructureRepository = feeStructureRepository;
		this.feeStructureInstallmentRepository = feeStructureInstallmentRepository;
	}

	@PostMapping
	@RequirePermission("FEE_ASSIGNMENT_MANAGE")
	public StudentFeeAssignmentResponse assign(@RequestBody AssignFeeRequest request) {
		StudentEnrollment enrollment = studentEnrollmentRepository.findById(request.studentEnrollmentId())
				.orElseThrow(() -> new IllegalArgumentException("No student enrollment with id " + request.studentEnrollmentId()));
		FeeStructure feeStructure = feeStructureRepository.findById(request.feeStructureId())
				.orElseThrow(() -> new IllegalArgumentException("No fee structure with id " + request.feeStructureId()));

		StudentFeeAssignment assignment = feeService.assignFee(
				enrollment, feeStructure, request.discountAmount(), request.discountReason(), request.approvedBy());
		return StudentFeeAssignmentResponse.from(assignment);
	}

	@PostMapping("/{assignmentId}/revise")
	@RequirePermission("FEE_ASSIGNMENT_MANAGE")
	public StudentFeeAssignmentResponse revise(@PathVariable Long assignmentId, @RequestBody ReviseAssignmentRequest request) {
		StudentFeeAssignment assignment = findAssignment(assignmentId);
		StudentFeeAssignment revised =
				feeService.reviseAssignment(assignment, request.discountAmount(), request.discountReason(), request.approvedBy());
		return StudentFeeAssignmentResponse.from(revised);
	}

	@PostMapping("/{assignmentId}/invoices")
	@RequirePermission("FEE_INVOICE_MANAGE")
	public InvoiceResponse generateInvoice(@PathVariable Long assignmentId, @RequestBody GenerateInvoiceRequest request) {
		StudentFeeAssignment assignment = findAssignment(assignmentId);
		FeeStructureInstallment installment = feeStructureInstallmentRepository.findById(request.feeStructureInstallmentId())
				.orElseThrow(() -> new IllegalArgumentException("No fee structure installment with id " + request.feeStructureInstallmentId()));

		Invoice invoice = feeService.generateInvoice(assignment, installment);
		return InvoiceResponse.from(invoice);
	}

	@GetMapping("/{assignmentId}")
	public StudentFeeAssignmentResponse get(@PathVariable Long assignmentId) {
		return StudentFeeAssignmentResponse.from(findAssignment(assignmentId));
	}

	@GetMapping
	public List<StudentFeeAssignmentResponse> list(@RequestParam Long studentEnrollmentId) {
		return studentFeeAssignmentRepository.findByStudentEnrollmentId(studentEnrollmentId).stream()
				.map(StudentFeeAssignmentResponse::from)
				.toList();
	}

	private StudentFeeAssignment findAssignment(Long assignmentId) {
		return studentFeeAssignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new IllegalArgumentException("No fee assignment with id " + assignmentId));
	}
}
