package com.operion.finance;

import java.math.BigDecimal;

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
 * One Razorpay order created for a payment link, with the amount quoted on it.
 * PaymentGatewayOrder only keeps the latest gatewayOrderId, but a parent can still pay
 * against an earlier Razorpay order (a second browser tab, or an order created before the
 * outstanding balance changed) - Razorpay captures that payment regardless, so the webhook
 * must still be able to find the link and the quoted amount for it. That is the only thing
 * this row is for; it is never edited after creation.
 */
@Getter
@Entity
@Table(name = "payment_gateway_attempts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentGatewayAttempt extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "payment_gateway_order_id")
	private PaymentGatewayOrder order;

	@Column(name = "gateway_order_id", nullable = false, length = 100)
	private String gatewayOrderId;

	/** The outstanding balance this Razorpay order was created for, in rupees. */
	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

	public PaymentGatewayAttempt(PaymentGatewayOrder order, String gatewayOrderId, BigDecimal amount) {
		this.order = order;
		this.gatewayOrderId = gatewayOrderId;
		this.amount = amount;
	}
}
