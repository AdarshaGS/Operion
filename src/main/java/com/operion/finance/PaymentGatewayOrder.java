package com.operion.finance;

import java.math.BigDecimal;
import java.time.Instant;

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

/**
 * A "pay this invoice" link - linkToken is the whole authorization (possession of the
 * link, same trust model as any real payment link e.g. Stripe/Razorpay Payment Links),
 * not stored hashed like PortalInvite's token since the blast radius of a leaked link is
 * "someone else can pay this one fixed invoice," not an identity compromise. gatewayOrderId
 * and amount are null until initiateCheckout actually creates the order with the gateway -
 * amount is the outstanding balance at that exact moment, not snapshotted at link-creation
 * time, so a partial payment made through another channel in between is reflected
 * correctly. One invoice per link for v1 - PaymentAllocation already supports one payment
 * covering several invoices, so "pay all outstanding" is a natural extension later, not a
 * rework. See ai-context/load-context.md's Fees section for Invoice/Payment/PaymentAllocation.
 */
@Getter
@Entity
@Table(name = "payment_gateway_orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentGatewayOrder extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "invoice_id")
	private Invoice invoice;

	/** Nullable - set once claimed by a real Payment when the webhook confirms it. */
	@ManyToOne
	@JoinColumn(name = "payment_id")
	private Payment payment;

	@Column(name = "link_token", nullable = false, length = 64)
	private String linkToken;

	/** Nullable until initiateCheckout creates the order with the gateway. */
	@Column(name = "gateway_order_id", length = 100)
	private String gatewayOrderId;

	/** Nullable until initiateCheckout - the outstanding balance at that moment, not at link-creation time. */
	@Column(precision = 10, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PaymentGatewayOrderStatus status;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	public PaymentGatewayOrder(Invoice invoice, String linkToken, Instant expiresAt) {
		this.invoice = invoice;
		this.linkToken = linkToken;
		this.status = PaymentGatewayOrderStatus.PENDING;
		this.expiresAt = expiresAt;
	}

	public void attachGatewayOrder(String gatewayOrderId, BigDecimal amount) {
		this.gatewayOrderId = gatewayOrderId;
		this.amount = amount;
	}

	/** Idempotency guard - a gateway webhook can be delivered more than once for the same event. */
	public void markPaid(Payment payment) {
		if (status == PaymentGatewayOrderStatus.PAID) {
			throw new IllegalStateException("Payment gateway order " + getId() + " is already PAID");
		}
		this.payment = payment;
		this.status = PaymentGatewayOrderStatus.PAID;
	}
}
