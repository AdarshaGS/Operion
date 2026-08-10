package com.operion.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
import com.operion.organisation.AcademicYear;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One per (StudentFeeAssignment, FeeStructureInstallment) - matches how schools actually
 * invoice (Term 1/Term 2, each with its own due date), generated explicitly by
 * FeeService.generateInvoice, never ad-hoc. totalAmount is the installment amount
 * proportionally discounted (installment.amount * effectiveAmount/baseAmount, HALF_UP to
 * 2dp) and stored once at generation, never recomputed. amountPaid is a stored, indexed
 * column (outstanding = totalAmount - amountPaid), not computed live via SUM - collection
 * reports need to range-scan thousands of invoices. Drift is prevented by exactly one
 * transactional writer: FeeService updates amountPaid in the same transaction as the
 * PaymentAllocation/Refund write. Per ai-context/erp-system-plan.md §3.2.
 */
@Getter
@Entity
@Table(name = "invoices")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invoice extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "academic_year_id")
	private AcademicYear academicYear;

	@ManyToOne(optional = false)
	@JoinColumn(name = "student_fee_assignment_id")
	private StudentFeeAssignment studentFeeAssignment;

	@ManyToOne(optional = false)
	@JoinColumn(name = "fee_structure_installment_id")
	private FeeStructureInstallment feeStructureInstallment;

	@Column(name = "invoice_number", nullable = false, length = 50)
	private String invoiceNumber;

	@Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal totalAmount;

	@Column(name = "amount_paid", nullable = false, precision = 10, scale = 2)
	private BigDecimal amountPaid;

	@Column(name = "due_date", nullable = false)
	private LocalDate dueDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private InvoiceStatus status;

	public Invoice(AcademicYear academicYear, StudentFeeAssignment studentFeeAssignment,
			FeeStructureInstallment feeStructureInstallment, String invoiceNumber, BigDecimal totalAmount, LocalDate dueDate) {
		this.academicYear = academicYear;
		this.studentFeeAssignment = studentFeeAssignment;
		this.feeStructureInstallment = feeStructureInstallment;
		this.invoiceNumber = invoiceNumber;
		this.totalAmount = totalAmount;
		this.amountPaid = BigDecimal.ZERO;
		this.dueDate = dueDate;
		this.status = InvoiceStatus.ISSUED;
	}

	public BigDecimal getOutstanding() {
		return totalAmount.subtract(amountPaid);
	}

	/** Called by FeeService within the same transaction as the PaymentAllocation write. */
	public void applyPayment(BigDecimal amount) {
		this.amountPaid = this.amountPaid.add(amount);
		this.status = amountPaid.compareTo(totalAmount) >= 0 ? InvoiceStatus.PAID : InvoiceStatus.PARTIALLY_PAID;
	}

	/** Called by FeeService on a bounce or a refund - additive reversal, never edits the original payment. */
	public void reversePayment(BigDecimal amount) {
		this.amountPaid = this.amountPaid.subtract(amount);
		this.status = amountPaid.compareTo(BigDecimal.ZERO) <= 0 ? InvoiceStatus.ISSUED : InvoiceStatus.PARTIALLY_PAID;
	}
}
