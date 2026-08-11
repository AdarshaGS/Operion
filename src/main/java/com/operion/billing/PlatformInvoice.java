package com.operion.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.operion.common.BaseEntity;
import com.operion.organisation.Organisation;
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
 * The platform's own bill to a school for a subscription period - distinct from
 * finance.Invoice, which is a school's per-student fee invoice to its own families.
 * studentCountAtBilling is a snapshot taken live off the enrollment count at generation
 * time, not a separately-tracked Usage entity - see the module design notes for why a
 * standalone usage table isn't earning its keep yet at annual, non-prorated pricing.
 * Payment collection itself (a gateway integration) is out of scope - markPaid is a
 * manual platform-admin action.
 */
@Getter
@Entity
@Table(name = "platform_invoices")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformInvoice extends BaseEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "organisation_id")
	private Organisation organisation;

	@ManyToOne(optional = false)
	@JoinColumn(name = "subscription_id")
	private Subscription subscription;

	@Column(name = "period_start", nullable = false)
	private LocalDate periodStart;

	@Column(name = "period_end", nullable = false)
	private LocalDate periodEnd;

	@Column(name = "student_count_at_billing", nullable = false)
	private Integer studentCountAtBilling;

	@Column(nullable = false)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PlatformInvoiceStatus status;

	@Column(name = "issued_at", nullable = false)
	private Instant issuedAt;

	@Column(name = "due_date", nullable = false)
	private LocalDate dueDate;

	/** Nullable - null until markPaid. */
	@Column(name = "paid_at")
	private Instant paidAt;

	public PlatformInvoice(Organisation organisation, Subscription subscription, LocalDate periodStart, LocalDate periodEnd,
			Integer studentCountAtBilling, BigDecimal amount, LocalDate dueDate) {
		this.organisation = organisation;
		this.subscription = subscription;
		this.periodStart = periodStart;
		this.periodEnd = periodEnd;
		this.studentCountAtBilling = studentCountAtBilling;
		this.amount = amount;
		this.status = PlatformInvoiceStatus.ISSUED;
		this.issuedAt = Instant.now();
		this.dueDate = dueDate;
	}

	public void markPaid(Instant paidAt) {
		if (status == PlatformInvoiceStatus.PAID) {
			throw new IllegalStateException("Invoice is already paid");
		}
		this.status = PlatformInvoiceStatus.PAID;
		this.paidAt = paidAt;
	}
}
