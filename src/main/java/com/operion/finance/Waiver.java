package com.operion.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Forgives some or all of an already-issued invoice's outstanding balance - distinct from
 * StudentFeeAssignment's discountAmount, which applies before an invoice even exists.
 * Additive/insert-only, same shape as Refund but with no Payment link (nothing was
 * received, so there's nothing to reverse). amount is always positive and capped at the
 * invoice's outstanding balance at the time it's applied - see Invoice.applyWaiver(). Per
 * #131.
 */
@Getter
@Entity
@Table(name = "waivers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Waiver extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "invoice_id")
	private Invoice invoice;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false, length = 500)
	private String reason;

	@Column(name = "approved_by", nullable = false)
	private Long approvedBy;

	@Column(name = "waiver_date", nullable = false)
	private LocalDate waiverDate;

	/** Nullable - an uploaded supporting document reference (see AssetStorageService), not
	 * every approval has one on file. Per #280. */
	@Column(name = "proof_file_reference")
	private String proofFileReference;

	/** Nullable - the original filename, for display; see proofFileReference. */
	@Column(name = "proof_file_name")
	private String proofFileName;

	public Waiver(Invoice invoice, BigDecimal amount, String reason, Long approvedBy, LocalDate waiverDate,
			String proofFileReference, String proofFileName) {
		this.invoice = invoice;
		this.amount = amount;
		this.reason = reason;
		this.approvedBy = approvedBy;
		this.waiverDate = waiverDate;
		this.proofFileReference = proofFileReference;
		this.proofFileName = proofFileName;
	}
}
