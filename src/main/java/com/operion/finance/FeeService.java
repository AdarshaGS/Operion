package com.operion.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import com.operion.academic.SchoolClass;
import com.operion.organisation.AcademicYear;
import com.operion.student.StudentEnrollment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns fee structure setup, student fee assignment (with discount), invoice generation,
 * and the payment/bounce/refund ledger. Money is never edited or deleted here - a wrong
 * payment is bounced, a wrong collection is refunded, both additive reversing events;
 * a wrong fee assignment is superseded once an invoice exists off it. Per
 * ai-context/erp-system-plan.md §3.2.
 */
@Service
public class FeeService {

	private final FeeCategoryRepository feeCategoryRepository;
	private final FeeStructureRepository feeStructureRepository;
	private final FeeStructureInstallmentRepository feeStructureInstallmentRepository;
	private final StudentFeeAssignmentRepository studentFeeAssignmentRepository;
	private final InvoiceRepository invoiceRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentAllocationRepository paymentAllocationRepository;
	private final RefundRepository refundRepository;
	private final FeeDocumentCounterRepository feeDocumentCounterRepository;

	public FeeService(FeeCategoryRepository feeCategoryRepository, FeeStructureRepository feeStructureRepository,
			FeeStructureInstallmentRepository feeStructureInstallmentRepository,
			StudentFeeAssignmentRepository studentFeeAssignmentRepository, InvoiceRepository invoiceRepository,
			PaymentRepository paymentRepository, PaymentAllocationRepository paymentAllocationRepository,
			RefundRepository refundRepository, FeeDocumentCounterRepository feeDocumentCounterRepository) {
		this.feeCategoryRepository = feeCategoryRepository;
		this.feeStructureRepository = feeStructureRepository;
		this.feeStructureInstallmentRepository = feeStructureInstallmentRepository;
		this.studentFeeAssignmentRepository = studentFeeAssignmentRepository;
		this.invoiceRepository = invoiceRepository;
		this.paymentRepository = paymentRepository;
		this.paymentAllocationRepository = paymentAllocationRepository;
		this.refundRepository = refundRepository;
		this.feeDocumentCounterRepository = feeDocumentCounterRepository;
	}

	public FeeCategory createCategory(String code, String name, String description) {
		return feeCategoryRepository.save(new FeeCategory(code, name, description));
	}

	/** Validates the installments sum to exactly the structure's amount before saving anything. */
	@Transactional
	public FeeStructure createFeeStructure(AcademicYear academicYear, SchoolClass schoolClass, FeeCategory feeCategory,
			BigDecimal amount, List<InstallmentInput> installments) {
		BigDecimal installmentTotal = installments.stream().map(InstallmentInput::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
		if (installmentTotal.compareTo(amount) != 0) {
			throw new IllegalArgumentException(
					"Installment amounts (" + installmentTotal + ") must sum to the structure amount (" + amount + ")");
		}

		FeeStructure structure = feeStructureRepository.save(new FeeStructure(academicYear, schoolClass, feeCategory, amount));
		for (InstallmentInput input : installments) {
			feeStructureInstallmentRepository.save(
					new FeeStructureInstallment(structure, input.installmentNumber(), input.dueDate(), input.amount()));
		}
		return structure;
	}

	/** discountAmount may be null/zero; a non-zero discount requires a reason and an approver. */
	@Transactional
	public StudentFeeAssignment assignFee(StudentEnrollment studentEnrollment, FeeStructure feeStructure,
			BigDecimal discountAmount, String discountReason, Long approvedBy) {
		if (!studentEnrollment.getAcademicYear().getId().equals(feeStructure.getAcademicYear().getId())) {
			throw new IllegalArgumentException("Enrollment and fee structure are for different academic years");
		}
		if (!studentEnrollment.getSection().getSchoolClass().getId().equals(feeStructure.getSchoolClass().getId())) {
			throw new IllegalArgumentException("Enrollment and fee structure are for different classes");
		}

		BigDecimal discount = normalizeDiscount(discountAmount);
		requireApprovalIfDiscounted(discount, discountReason, approvedBy);

		BigDecimal effective = feeStructure.getAmount().subtract(discount);
		return studentFeeAssignmentRepository.save(
				new StudentFeeAssignment(studentEnrollment, feeStructure, feeStructure.getAmount(), discount, effective, discountReason, approvedBy));
	}

	/**
	 * Pre-invoice, mutates the existing row in place. Once any Invoice exists off it,
	 * closes it (SUPERSEDED) and inserts a new row instead - see StudentFeeAssignment.
	 */
	@Transactional
	public StudentFeeAssignment reviseAssignment(
			StudentFeeAssignment assignment, BigDecimal discountAmount, String discountReason, Long approvedBy) {
		BigDecimal discount = normalizeDiscount(discountAmount);
		requireApprovalIfDiscounted(discount, discountReason, approvedBy);
		BigDecimal effective = assignment.getBaseAmount().subtract(discount);

		if (invoiceRepository.existsByStudentFeeAssignmentId(assignment.getId())) {
			assignment.supersede();
			studentFeeAssignmentRepository.save(assignment);
			return studentFeeAssignmentRepository.save(new StudentFeeAssignment(assignment.getStudentEnrollment(),
					assignment.getFeeStructure(), assignment.getBaseAmount(), discount, effective, discountReason, approvedBy));
		}
		assignment.updateDiscount(discount, effective, discountReason, approvedBy);
		return studentFeeAssignmentRepository.save(assignment);
	}

	/** One invoice per (assignment, installment) - rejects a second generation for the same pair. */
	@Transactional
	public Invoice generateInvoice(StudentFeeAssignment assignment, FeeStructureInstallment installment) {
		if (assignment.getStatus() != StudentFeeAssignmentStatus.ACTIVE) {
			throw new IllegalStateException("Fee assignment " + assignment.getId() + " is not ACTIVE, cannot invoice");
		}
		if (!installment.getFeeStructure().getId().equals(assignment.getFeeStructure().getId())) {
			throw new IllegalArgumentException("Installment does not belong to the assignment's fee structure");
		}
		invoiceRepository.findByStudentFeeAssignmentIdAndFeeStructureInstallmentId(assignment.getId(), installment.getId())
				.ifPresent(existing -> {
					throw new IllegalStateException("Invoice already generated for installment " + installment.getId());
				});

		BigDecimal totalAmount = installment.getAmount()
				.multiply(assignment.getEffectiveAmount())
				.divide(assignment.getBaseAmount(), 2, RoundingMode.HALF_UP);

		AcademicYear academicYear = assignment.getStudentEnrollment().getAcademicYear();
		String invoiceNumber = nextDocumentNumber(academicYear, FeeDocumentType.INVOICE);
		return invoiceRepository.save(new Invoice(academicYear, assignment, installment, invoiceNumber, totalAmount, installment.getDueDate()));
	}

	/** Sum of allocations must exactly equal the payment amount - no partially-allocated or over-allocated payments. */
	@Transactional
	public Payment recordPayment(AcademicYear academicYear, BigDecimal amount, PaymentMethod paymentMethod,
			LocalDate paymentDate, String remarks, List<AllocationInput> allocations) {
		BigDecimal allocatedTotal = allocations.stream().map(AllocationInput::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
		if (allocatedTotal.compareTo(amount) != 0) {
			throw new IllegalArgumentException("Allocated amount (" + allocatedTotal + ") must equal the payment amount (" + amount + ")");
		}

		String receiptNumber = nextDocumentNumber(academicYear, FeeDocumentType.PAYMENT);
		Payment payment = paymentRepository.save(new Payment(academicYear, receiptNumber, amount, paymentMethod, paymentDate, remarks));

		for (AllocationInput input : allocations) {
			Invoice invoice = invoiceRepository.findById(input.invoiceId())
					.orElseThrow(() -> new IllegalArgumentException("No invoice with id " + input.invoiceId()));
			paymentAllocationRepository.save(new PaymentAllocation(payment, invoice, input.amount()));
			invoice.applyPayment(input.amount());
			invoiceRepository.save(invoice);
		}
		return payment;
	}

	/** Reverses every allocation's effect on its invoice's amountPaid - never deletes the payment or its allocations. */
	@Transactional
	public Payment bouncePayment(Payment payment) {
		payment.markBounced();
		for (PaymentAllocation allocation : paymentAllocationRepository.findByPaymentId(payment.getId())) {
			Invoice invoice = allocation.getInvoice();
			invoice.reversePayment(allocation.getAllocatedAmount());
			invoiceRepository.save(invoice);
		}
		return paymentRepository.save(payment);
	}

	@Transactional
	public Refund recordRefund(Payment payment, Invoice invoice, BigDecimal amount, String reason, Long approvedBy, LocalDate refundDate) {
		invoice.reversePayment(amount);
		invoiceRepository.save(invoice);
		return refundRepository.save(new Refund(payment, invoice, amount, reason, approvedBy, refundDate));
	}

	/** Atomic per-(organisation, academicYear, documentType) sequence - never SELECT MAX()+1. */
	private String nextDocumentNumber(AcademicYear academicYear, FeeDocumentType documentType) {
		FeeDocumentCounter counter = feeDocumentCounterRepository
				.findByAcademicYearIdAndDocumentType(academicYear.getId(), documentType)
				.orElseGet(() -> feeDocumentCounterRepository.save(new FeeDocumentCounter(academicYear, documentType)));
		long number = counter.consumeNext();
		feeDocumentCounterRepository.save(counter);
		String prefix = documentType == FeeDocumentType.INVOICE ? "INV" : "RCT";
		return prefix + "-" + academicYear.getName() + "-" + String.format("%06d", number);
	}

	private BigDecimal normalizeDiscount(BigDecimal discountAmount) {
		return discountAmount == null ? BigDecimal.ZERO : discountAmount;
	}

	private void requireApprovalIfDiscounted(BigDecimal discount, String discountReason, Long approvedBy) {
		if (discount.compareTo(BigDecimal.ZERO) > 0 && (discountReason == null || approvedBy == null)) {
			throw new IllegalArgumentException("A discount requires both a reason and an approver");
		}
	}

	public record InstallmentInput(int installmentNumber, LocalDate dueDate, BigDecimal amount) {
	}

	public record AllocationInput(Long invoiceId, BigDecimal amount) {
	}
}
