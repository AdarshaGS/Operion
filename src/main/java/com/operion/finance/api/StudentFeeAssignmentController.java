package com.operion.finance.api;

import java.math.BigDecimal;
import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.finance.FeeReminderService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private static final Logger log = LoggerFactory.getLogger(StudentFeeAssignmentController.class);

	private final FeeService feeService;
	private final FeeReminderService feeReminderService;
	private final StudentFeeAssignmentRepository studentFeeAssignmentRepository;
	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final FeeStructureRepository feeStructureRepository;
	private final FeeStructureInstallmentRepository feeStructureInstallmentRepository;
	private final FeeApproverPolicy feeApproverPolicy;

	public StudentFeeAssignmentController(FeeService feeService, FeeReminderService feeReminderService,
			StudentFeeAssignmentRepository studentFeeAssignmentRepository,
			StudentEnrollmentRepository studentEnrollmentRepository, FeeStructureRepository feeStructureRepository,
			FeeStructureInstallmentRepository feeStructureInstallmentRepository, FeeApproverPolicy feeApproverPolicy) {
		this.feeApproverPolicy = feeApproverPolicy;
		this.feeService = feeService;
		this.feeReminderService = feeReminderService;
		this.studentFeeAssignmentRepository = studentFeeAssignmentRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.feeStructureRepository = feeStructureRepository;
		this.feeStructureInstallmentRepository = feeStructureInstallmentRepository;
	}

	@PostMapping
	@RequirePermission("FEE_ASSIGNMENT_MANAGE")
	public StudentFeeAssignmentResponse assign(@RequestBody AssignFeeRequest request) {
		RequestChecks.required(request.studentEnrollmentId(), "studentEnrollmentId");
		RequestChecks.required(request.feeStructureId(), "feeStructureId");
		StudentEnrollment enrollment = studentEnrollmentRepository.findById(request.studentEnrollmentId())
				.orElseThrow(() -> new IllegalArgumentException("No student enrollment with id " + request.studentEnrollmentId()));
		FeeStructure feeStructure = feeStructureRepository.findById(request.feeStructureId())
				.orElseThrow(() -> new IllegalArgumentException("No fee structure with id " + request.feeStructureId()));

		requireDiscountSignOff(request.discountAmount());
		feeApproverPolicy.requireEligible(request.approvedBy());
		StudentFeeAssignment assignment = feeService.assignFee(
				enrollment, feeStructure, request.discountAmount(), request.discountReason(), request.approvedBy(),
				request.paymentFrequency(), request.proofFileReference(), request.proofFileName());
		return StudentFeeAssignmentResponse.from(assignment);
	}

	@PostMapping("/{assignmentId}/revise")
	@RequirePermission("FEE_ASSIGNMENT_MANAGE")
	public StudentFeeAssignmentResponse revise(@PathVariable Long assignmentId, @RequestBody ReviseAssignmentRequest request) {
		StudentFeeAssignment assignment = findAssignment(assignmentId);
		requireDiscountSignOff(request.discountAmount());
		feeApproverPolicy.requireEligible(request.approvedBy());
		StudentFeeAssignment revised =
				feeService.reviseAssignment(assignment, request.discountAmount(), request.discountReason(), request.approvedBy(),
						request.paymentFrequency(), request.proofFileReference(), request.proofFileName());
		return StudentFeeAssignmentResponse.from(revised);
	}

	@PostMapping("/{assignmentId}/invoices")
	@RequirePermission("FEE_INVOICE_MANAGE")
	public InvoiceResponse generateInvoice(@PathVariable Long assignmentId, @RequestBody GenerateInvoiceRequest request) {
		StudentFeeAssignment assignment = findAssignment(assignmentId);
		RequestChecks.required(request.feeStructureInstallmentId(), "feeStructureInstallmentId");
		FeeStructureInstallment installment = feeStructureInstallmentRepository.findById(request.feeStructureInstallmentId())
				.orElseThrow(() -> new IllegalArgumentException("No fee structure installment with id " + request.feeStructureInstallmentId()));

		Invoice invoice = feeService.generateInvoice(assignment, installment);
		// The invoice is already committed - a failed guardian nudge (e.g. two concurrent
		// first invoices racing to create the notification template) must not fail the request.
		try {
			feeReminderService.notifyInvoiceGenerated(invoice);
		} catch (RuntimeException ex) {
			log.warn("Invoice {} generated but its guardian notification failed", invoice.getInvoiceNumber(), ex);
		}
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

	private void requireDiscountSignOff(BigDecimal discountAmount) {
		if (discountAmount != null && discountAmount.signum() != 0) {
			feeApproverPolicy.requireDiscountPermission();
		}
	}

	private StudentFeeAssignment findAssignment(Long assignmentId) {
		return studentFeeAssignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new IllegalArgumentException("No fee assignment with id " + assignmentId));
	}
}
