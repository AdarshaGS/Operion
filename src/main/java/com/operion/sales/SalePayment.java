package com.operion.sales;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
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

/** A payment applied against a Sale - cash/manual methods only, no gateway integration
 * for v1 (GitHub #62). Multiple payments can accumulate against one Sale. */
@Getter
@Entity
@Table(name = "sale_payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalePayment extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "sale_id")
	private Sale sale;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_method", nullable = false, length = 20)
	private PaymentMethod paymentMethod;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

	@Column(name = "paid_at", nullable = false)
	private LocalDate paidAt;

	public SalePayment(Sale sale, PaymentMethod paymentMethod, BigDecimal amount, LocalDate paidAt) {
		this.sale = sale;
		this.paymentMethod = paymentMethod;
		this.amount = amount;
		this.paidAt = paidAt;
	}
}
