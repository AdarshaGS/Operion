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
 * A money-received event. receiptNumber uses the same per-(org, academic year) atomic
 * counter as Invoice.invoiceNumber - a receipt is just a rendering of a Payment plus its
 * PaymentAllocations, not a separate entity; reprints re-render existing data. Starts
 * CLEARED (cash/UPI/card/bank transfer settle immediately); markBounced() is available on
 * any payment (practically: cheques) and reverses its allocations' effect on each
 * Invoice's amountPaid via FeeService, never deletes/edits this row or its allocations.
 * Per ai-context/erp-system-plan.md §3.2.
 */
@Getter
@Entity
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "academic_year_id")
	private AcademicYear academicYear;

	@Column(name = "receipt_number", nullable = false, length = 50)
	private String receiptNumber;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_method", nullable = false, length = 20)
	private PaymentMethod paymentMethod;

	@Column(name = "payment_date", nullable = false)
	private LocalDate paymentDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PaymentStatus status;

	/** Nullable. */
	private String remarks;

	/** Nullable - the mode-specific reference the payer quotes (UPI UTR, cheque number,
	 * bank transfer reference, card/gateway transaction id); not applicable to CASH. Per
	 * #282. */
	@Column(name = "payment_reference", length = 100)
	private String paymentReference;

	public Payment(AcademicYear academicYear, String receiptNumber, BigDecimal amount, PaymentMethod paymentMethod,
			LocalDate paymentDate, String remarks) {
		this(academicYear, receiptNumber, amount, paymentMethod, paymentDate, remarks, null);
	}

	public Payment(AcademicYear academicYear, String receiptNumber, BigDecimal amount, PaymentMethod paymentMethod,
			LocalDate paymentDate, String remarks, String paymentReference) {
		this.academicYear = academicYear;
		this.receiptNumber = receiptNumber;
		this.amount = amount;
		this.paymentMethod = paymentMethod;
		this.paymentDate = paymentDate;
		this.remarks = remarks;
		this.paymentReference = paymentReference;
		this.status = PaymentStatus.CLEARED;
	}

	public void markBounced() {
		if (status == PaymentStatus.BOUNCED) {
			throw new IllegalStateException("Payment " + getId() + " is already BOUNCED");
		}
		this.status = PaymentStatus.BOUNCED;
	}
}
