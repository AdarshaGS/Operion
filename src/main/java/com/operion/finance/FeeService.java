package com.operion.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.operion.academic.SchoolClass;
import com.operion.audit.AuditLogService;
import com.operion.common.InvalidRequestException;
import com.operion.common.TenantContext;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.DocumentNumberFormatter;
import com.operion.organisation.OrganisationBranding;
import com.operion.organisation.OrganisationBrandingRepository;
import com.operion.student.StudentEnrollment;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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
	private final FeeStructureGroupRepository feeStructureGroupRepository;
	private final FeeStructureRepository feeStructureRepository;
	private final FeeStructureInstallmentRepository feeStructureInstallmentRepository;
	private final StudentFeeAssignmentRepository studentFeeAssignmentRepository;
	private final InvoiceRepository invoiceRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentAllocationRepository paymentAllocationRepository;
	private final RefundRepository refundRepository;
	private final AdjustmentRepository adjustmentRepository;
	private final WaiverRepository waiverRepository;
	private final FeeDocumentCounterRepository feeDocumentCounterRepository;
	private final OrganisationBrandingRepository organisationBrandingRepository;
	// ObjectProvider, not AuditLogService directly: FeeService is @Import-ed into ~a dozen
	// @DataJpaTest slices that have no ObjectMapper bean (see AuditLogService's dependency), so
	// a hard dependency would break every one of them. Always present in the real app.
	private final ObjectProvider<AuditLogService> auditLogService;
	// Optional field, not a constructor arg: slices and tests that build FeeService by hand
	// have no such bean and fall back to creating the counter inline (fine single-threaded).
	@Autowired(required = false)
	private FeeDocumentCounterInitializer counterInitializer;

	public FeeService(FeeCategoryRepository feeCategoryRepository, FeeStructureGroupRepository feeStructureGroupRepository,
			FeeStructureRepository feeStructureRepository, FeeStructureInstallmentRepository feeStructureInstallmentRepository,
			StudentFeeAssignmentRepository studentFeeAssignmentRepository, InvoiceRepository invoiceRepository,
			PaymentRepository paymentRepository, PaymentAllocationRepository paymentAllocationRepository,
			RefundRepository refundRepository, AdjustmentRepository adjustmentRepository, WaiverRepository waiverRepository,
			FeeDocumentCounterRepository feeDocumentCounterRepository, OrganisationBrandingRepository organisationBrandingRepository,
			ObjectProvider<AuditLogService> auditLogService) {
		this.feeCategoryRepository = feeCategoryRepository;
		this.feeStructureGroupRepository = feeStructureGroupRepository;
		this.feeStructureRepository = feeStructureRepository;
		this.feeStructureInstallmentRepository = feeStructureInstallmentRepository;
		this.studentFeeAssignmentRepository = studentFeeAssignmentRepository;
		this.invoiceRepository = invoiceRepository;
		this.paymentRepository = paymentRepository;
		this.paymentAllocationRepository = paymentAllocationRepository;
		this.refundRepository = refundRepository;
		this.adjustmentRepository = adjustmentRepository;
		this.waiverRepository = waiverRepository;
		this.feeDocumentCounterRepository = feeDocumentCounterRepository;
		this.organisationBrandingRepository = organisationBrandingRepository;
		this.auditLogService = auditLogService;
	}

	private void audit(String entityType, Long entityId, String action, Object before, Object after) {
		auditLogService.ifAvailable(service -> service.record(entityType, entityId, action, before, after));
	}

	public FeeCategory createCategory(String code, String name, String description, FeeCategoryType categoryType) {
		return feeCategoryRepository.save(new FeeCategory(code, name, description, categoryType));
	}

	/** The named "one fee structure, several components" setup - see FeeStructureGroup. */
	@Transactional
	public FeeStructureGroup createFeeStructureGroup(String name, AcademicYear academicYear, SchoolClass schoolClass) {
		return feeStructureGroupRepository.save(new FeeStructureGroup(name, academicYear, schoolClass));
	}

	/** Validates the installments sum to exactly the structure's amount before saving anything. */
	@Transactional
	public FeeStructure createFeeStructure(FeeStructureGroup feeStructureGroup, FeeCategory feeCategory,
			BigDecimal amount, List<InstallmentInput> installments) {
		return createFeeStructure(feeStructureGroup, feeCategory, amount, installments, PaymentFrequency.MONTHLY, null);
	}

	@Transactional
	public FeeStructure createFeeStructure(FeeStructureGroup feeStructureGroup, FeeCategory feeCategory, BigDecimal amount,
			List<InstallmentInput> installments, PaymentFrequency paymentFrequency, BigDecimal oneShotDiscountAmount) {
		if (amount == null || amount.signum() <= 0) {
			throw new InvalidRequestException("A fee structure amount must be greater than zero");
		}
		if (installments == null || installments.isEmpty()) {
			throw new InvalidRequestException("A fee structure needs at least one installment");
		}
		if (installments.stream().anyMatch(i -> i.amount() == null || i.amount().signum() <= 0 || i.dueDate() == null)) {
			throw new InvalidRequestException("Every installment needs a due date and an amount greater than zero");
		}
		if (installments.stream().map(InstallmentInput::installmentNumber).distinct().count() != installments.size()) {
			throw new InvalidRequestException("Installment numbers must be unique within a fee structure");
		}
		if (oneShotDiscountAmount != null && (oneShotDiscountAmount.signum() < 0 || oneShotDiscountAmount.compareTo(amount) > 0)) {
			throw new InvalidRequestException("A one-shot discount must be between zero and the structure amount");
		}
		BigDecimal installmentTotal = installments.stream().map(InstallmentInput::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
		if (installmentTotal.compareTo(amount) != 0) {
			throw new InvalidRequestException(
					"Installment amounts (" + installmentTotal + ") must sum to the structure amount (" + amount + ")");
		}

		FeeStructure structure = feeStructureRepository.save(
				new FeeStructure(feeStructureGroup, feeCategory, amount, paymentFrequency, oneShotDiscountAmount));
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
		return assignFee(studentEnrollment, feeStructure, discountAmount, discountReason, approvedBy, null, null, null);
	}

	@Transactional
	public StudentFeeAssignment assignFee(StudentEnrollment studentEnrollment, FeeStructure feeStructure,
			BigDecimal discountAmount, String discountReason, Long approvedBy, PaymentFrequency paymentFrequency,
			String proofFileReference, String proofFileName) {
		FeeStructureGroup feeStructureGroup = feeStructure.getFeeStructureGroup();
		if (!studentEnrollment.getAcademicYear().getId().equals(feeStructureGroup.getAcademicYear().getId())) {
			throw new InvalidRequestException("Enrollment and fee structure are for different academic years");
		}
		if (!studentEnrollment.getSection().getSchoolClass().getId().equals(feeStructureGroup.getSchoolClass().getId())) {
			throw new InvalidRequestException("Enrollment and fee structure are for different classes");
		}
		boolean alreadyAssigned = studentFeeAssignmentRepository.findByStudentEnrollmentId(studentEnrollment.getId()).stream()
				.anyMatch(a -> a.getStatus() == StudentFeeAssignmentStatus.ACTIVE && a.getFeeStructure().getId().equals(feeStructure.getId()));
		if (alreadyAssigned) {
			throw new IllegalStateException("This fee structure is already assigned to the enrollment");
		}

		BigDecimal discount = normalizeDiscount(discountAmount);
		requireValidDiscount(discount, feeStructure.getAmount(), discountReason, approvedBy);

		BigDecimal effective = feeStructure.getAmount().subtract(discount);
		return studentFeeAssignmentRepository.save(
				new StudentFeeAssignment(studentEnrollment, feeStructure, feeStructure.getAmount(), discount, effective, discountReason, approvedBy,
						paymentFrequency, proofFileReference, proofFileName));
	}

	/**
	 * Pre-invoice, mutates the existing row in place. Once any Invoice exists off it,
	 * closes it (SUPERSEDED) and inserts a new row instead - see StudentFeeAssignment.
	 */
	@Transactional
	public StudentFeeAssignment reviseAssignment(
			StudentFeeAssignment assignment, BigDecimal discountAmount, String discountReason, Long approvedBy) {
		return reviseAssignment(assignment, discountAmount, discountReason, approvedBy, null, null, null);
	}

	/** A null paymentFrequency / proof keeps what the assignment already carries. */
	@Transactional
	public StudentFeeAssignment reviseAssignment(StudentFeeAssignment assignment, BigDecimal discountAmount, String discountReason,
			Long approvedBy, PaymentFrequency paymentFrequency, String proofFileReference, String proofFileName) {
		PaymentFrequency frequency = paymentFrequency != null ? paymentFrequency : assignment.getPaymentFrequency();
		String proofRef = proofFileReference != null ? proofFileReference : assignment.getProofFileReference();
		String proofName = proofFileReference != null ? proofFileName : assignment.getProofFileName();
		BigDecimal discount = normalizeDiscount(discountAmount);
		requireValidDiscount(discount, assignment.getBaseAmount(), discountReason, approvedBy);
		BigDecimal effective = assignment.getBaseAmount().subtract(discount);

		if (invoiceRepository.existsByStudentFeeAssignmentId(assignment.getId())) {
			assignment.supersede();
			studentFeeAssignmentRepository.save(assignment);
			return studentFeeAssignmentRepository.save(new StudentFeeAssignment(assignment.getStudentEnrollment(),
					assignment.getFeeStructure(), assignment.getBaseAmount(), discount, effective, discountReason, approvedBy,
					frequency, proofRef, proofName));
		}
		assignment.updateDiscount(discount, effective, discountReason, approvedBy);
		assignment.updateDetails(frequency, proofRef, proofName);
		return studentFeeAssignmentRepository.save(assignment);
	}

	/** One invoice per (assignment, installment) - rejects a second generation for the same pair. */
	@Transactional
	public Invoice generateInvoice(StudentFeeAssignment assignment, FeeStructureInstallment installment) {
		if (assignment.getStatus() != StudentFeeAssignmentStatus.ACTIVE) {
			throw new IllegalStateException("Fee assignment " + assignment.getId() + " is not ACTIVE, cannot invoice");
		}
		if (!installment.getFeeStructure().getId().equals(assignment.getFeeStructure().getId())) {
			throw new InvalidRequestException("Installment does not belong to the assignment's fee structure");
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
		return recordPayment(academicYear, amount, paymentMethod, paymentDate, remarks, allocations, null);
	}

	@Transactional
	public Payment recordPayment(AcademicYear academicYear, BigDecimal amount, PaymentMethod paymentMethod, LocalDate paymentDate,
			String remarks, List<AllocationInput> allocations, String paymentReference) {
		return recordPayment(academicYear, amount, paymentMethod, paymentDate, remarks, allocations, paymentReference, null);
	}

	@Transactional
	public Payment recordPayment(AcademicYear academicYear, BigDecimal amount, PaymentMethod paymentMethod, LocalDate paymentDate,
			String remarks, List<AllocationInput> allocations, String paymentReference, String paymentBankName) {
		if (amount == null || amount.signum() <= 0) {
			throw new InvalidRequestException("A payment amount must be greater than zero");
		}
		if (paymentDate == null) {
			throw new InvalidRequestException("A payment date is required");
		}
		if (allocations == null || allocations.isEmpty()) {
			throw new InvalidRequestException("A payment must be allocated to at least one invoice");
		}
		if (allocations.stream().anyMatch(a -> a.invoiceId() == null || a.amount() == null || a.amount().signum() <= 0)) {
			throw new InvalidRequestException("Every allocation needs an invoice and an amount greater than zero");
		}
		if (paymentMethod == PaymentMethod.CHEQUE
				&& (paymentReference == null || paymentReference.isBlank() || paymentBankName == null || paymentBankName.isBlank())) {
			throw new InvalidRequestException("A cheque payment needs both a cheque number and a bank name");
		}
		BigDecimal allocatedTotal = allocations.stream().map(AllocationInput::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
		if (allocatedTotal.compareTo(amount) != 0) {
			throw new InvalidRequestException("Allocated amount (" + allocatedTotal + ") must equal the payment amount (" + amount + ")");
		}

		String receiptNumber = nextDocumentNumber(academicYear, FeeDocumentType.PAYMENT);
		Payment payment = paymentRepository.save(
				new Payment(academicYear, receiptNumber, amount, paymentMethod, paymentDate, remarks, paymentReference, paymentBankName));

		for (AllocationInput input : allocations) {
			Invoice invoice = invoiceRepository.findById(input.invoiceId())
					.orElseThrow(() -> new IllegalArgumentException("No invoice with id " + input.invoiceId()));
			paymentAllocationRepository.save(new PaymentAllocation(payment, invoice, input.amount()));
			invoice.applyPayment(input.amount());
			invoiceRepository.save(invoice);
		}
		audit("Payment", payment.getId(), "PAYMENT_RECORDED", null, amount);
		return payment;
	}

	/** Reverses every allocation's effect on its invoice's amountPaid - never deletes the payment or its allocations. */
	@Transactional
	public Payment bouncePayment(Payment payment) {
		payment.markBounced();
		for (PaymentAllocation allocation : paymentAllocationRepository.findByPaymentId(payment.getId())) {
			Invoice invoice = allocation.getInvoice();
			BigDecimal alreadyRefunded = refundRepository.findByPaymentId(payment.getId()).stream()
					.filter(r -> r.getInvoice().getId().equals(invoice.getId()))
					.map(Refund::getAmount)
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			invoice.reversePayment(allocation.getAllocatedAmount().subtract(alreadyRefunded));
			invoiceRepository.save(invoice);
		}
		Payment saved = paymentRepository.save(payment);
		audit("Payment", saved.getId(), "PAYMENT_BOUNCED", null, saved.getAmount());
		return saved;
	}

	@Transactional
	public Refund recordRefund(Payment payment, Invoice invoice, BigDecimal amount, String reason, Long approvedBy, LocalDate refundDate,
			String proofFileReference, String proofFileName) {
		requireReasonAndApprover(reason, approvedBy);
		if (amount == null || amount.signum() <= 0) {
			throw new InvalidRequestException("A refund amount must be greater than zero");
		}
		if (payment.getStatus() == PaymentStatus.BOUNCED) {
			throw new InvalidRequestException("A bounced payment cannot be refunded");
		}
		BigDecimal paidToInvoice = paymentAllocationRepository.findByPaymentId(payment.getId()).stream()
				.filter(a -> a.getInvoice().getId().equals(invoice.getId()))
				.map(PaymentAllocation::getAllocatedAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		if (paidToInvoice.signum() == 0) {
			throw new InvalidRequestException("Payment " + payment.getId() + " did not pay invoice " + invoice.getId());
		}
		BigDecimal alreadyRefunded = refundRepository.findByPaymentId(payment.getId()).stream()
				.filter(r -> r.getInvoice().getId().equals(invoice.getId()))
				.map(Refund::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		if (amount.compareTo(paidToInvoice.subtract(alreadyRefunded)) > 0) {
			throw new InvalidRequestException("Refund " + amount + " exceeds what this payment still holds for the invoice ("
					+ paidToInvoice.subtract(alreadyRefunded) + ")");
		}
		invoice.reversePayment(amount);
		invoiceRepository.save(invoice);
		Refund refund = refundRepository.save(new Refund(payment, invoice, amount, reason, approvedBy, refundDate, proofFileReference, proofFileName));
		audit("Refund", refund.getId(), "PAYMENT_REFUNDED", null, amount);
		return refund;
	}

	/** Manual correction to what's owed on an invoice - amount may be positive or negative. Per #131. */
	@Transactional
	public Adjustment recordAdjustment(Invoice invoice, BigDecimal amount, String reason, Long approvedBy, LocalDate adjustmentDate,
			String proofFileReference, String proofFileName) {
		requireReasonAndApprover(reason, approvedBy);
		if (amount == null || amount.signum() == 0) {
			throw new InvalidRequestException("An adjustment amount cannot be zero");
		}
		invoice.applyAdjustment(amount);
		invoiceRepository.save(invoice);
		Adjustment adjustment = adjustmentRepository.save(
				new Adjustment(invoice, amount, reason, approvedBy, adjustmentDate, proofFileReference, proofFileName));
		audit("Adjustment", adjustment.getId(), "INVOICE_ADJUSTED", null, amount);
		return adjustment;
	}

	/** Forgives some or all of an already-issued invoice's outstanding balance. Per #131. */
	@Transactional
	public Waiver recordWaiver(Invoice invoice, BigDecimal amount, String reason, Long approvedBy, LocalDate waiverDate,
			String proofFileReference, String proofFileName) {
		requireReasonAndApprover(reason, approvedBy);
		if (amount == null || amount.signum() <= 0) {
			throw new InvalidRequestException("A waiver amount must be greater than zero");
		}
		invoice.applyWaiver(amount);
		invoiceRepository.save(invoice);
		Waiver waiver = waiverRepository.save(new Waiver(invoice, amount, reason, approvedBy, waiverDate, proofFileReference, proofFileName));
		audit("Waiver", waiver.getId(), "INVOICE_WAIVED", null, amount);
		return waiver;
	}

	/** Atomic per-(organisation, academicYear, documentType) sequence - never SELECT MAX()+1.
	 * Format is org-configurable (#142); see OrganisationBranding.invoiceNumberFormat/receiptNumberFormat. */
	private String nextDocumentNumber(AcademicYear academicYear, FeeDocumentType documentType) {
		ensureCounterExists(academicYear, documentType);
		FeeDocumentCounter counter = feeDocumentCounterRepository
				.findByAcademicYearIdAndDocumentType(academicYear.getId(), documentType)
				.orElseGet(() -> feeDocumentCounterRepository.save(new FeeDocumentCounter(academicYear, documentType)));
		long number = counter.consumeNext();
		feeDocumentCounterRepository.save(counter);

		Optional<OrganisationBranding> branding = organisationBrandingRepository.findById(TenantContext.getOrganisationId());
		String defaultTemplate =
				documentType == FeeDocumentType.INVOICE ? OrganisationBranding.DEFAULT_INVOICE_NUMBER_FORMAT : OrganisationBranding.DEFAULT_RECEIPT_NUMBER_FORMAT;
		String template = branding
				.map(b -> documentType == FeeDocumentType.INVOICE ? b.getInvoiceNumberFormat() : b.getReceiptNumberFormat())
				.orElse(defaultTemplate);
		return DocumentNumberFormatter.format(template, number, academicYear.getName(), academicYear.getStartDate().getYear());
	}

	/** Creates the counter row outside this transaction so the locking read below never has to
	 * lock a missing row - see FeeDocumentCounterInitializer for the deadlock that avoids. */
	private void ensureCounterExists(AcademicYear academicYear, FeeDocumentType documentType) {
		if (counterInitializer == null
				|| feeDocumentCounterRepository.existsByAcademicYearIdAndDocumentType(academicYear.getId(), documentType)) {
			return;
		}
		try {
			counterInitializer.create(academicYear, documentType);
		} catch (DataIntegrityViolationException alreadyCreatedConcurrently) {
			// another request created it first - the row exists, which is all we need
		}
	}

	private BigDecimal normalizeDiscount(BigDecimal discountAmount) {
		return discountAmount == null ? BigDecimal.ZERO : discountAmount;
	}

	private void requireValidDiscount(BigDecimal discount, BigDecimal baseAmount, String discountReason, Long approvedBy) {
		if (discount.signum() < 0) {
			throw new InvalidRequestException("A discount cannot be negative");
		}
		if (discount.compareTo(baseAmount) > 0) {
			throw new InvalidRequestException("A discount cannot be larger than the fee amount (" + baseAmount + ")");
		}
		if (discount.signum() > 0 && (discountReason == null || discountReason.isBlank() || approvedBy == null)) {
			throw new InvalidRequestException("A discount requires both a reason and an approver");
		}
	}

	private static void requireReasonAndApprover(String reason, Long approvedBy) {
		if (reason == null || reason.isBlank()) {
			throw new InvalidRequestException("A reason is required");
		}
		if (approvedBy == null) {
			throw new InvalidRequestException("An approver is required");
		}
	}

	public record InstallmentInput(int installmentNumber, LocalDate dueDate, BigDecimal amount) {
	}

	public record AllocationInput(Long invoiceId, BigDecimal amount) {
	}
}
