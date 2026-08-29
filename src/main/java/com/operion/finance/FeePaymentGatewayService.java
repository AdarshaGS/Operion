package com.operion.finance;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

import com.operion.common.TenantContext;
import com.operion.common.WebhookVerificationException;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Pay this invoice" links backed by Razorpay - see ai-context/load-context.md's Fees
 * section for FeeService/Invoice/Payment/PaymentAllocation, which this reuses rather
 * than duplicating payment-recording logic. Only a signed webhook from Razorpay ever
 * calls FeeService.recordPayment() here - a client-side "payment succeeded" callback is
 * never trusted on its own, since a parent closing the browser tab right after paying
 * would otherwise silently lose the payment. The frontend polls getLinkStatus() instead.
 */
@Service
public class FeePaymentGatewayService {

	private static final Duration LINK_VALIDITY = Duration.ofDays(3);
	private static final String CURRENCY = "INR";

	private final PaymentGatewayOrderRepository paymentGatewayOrderRepository;
	private final InvoiceRepository invoiceRepository;
	private final OrganisationRepository organisationRepository;
	private final FeeService feeService;
	private final RazorpayCredentialsProvider credentialsProvider;
	private final RazorpayGateway razorpayGateway;
	private final SecureRandom secureRandom = new SecureRandom();

	public FeePaymentGatewayService(PaymentGatewayOrderRepository paymentGatewayOrderRepository, InvoiceRepository invoiceRepository,
			OrganisationRepository organisationRepository, FeeService feeService, RazorpayCredentialsProvider credentialsProvider,
			RazorpayGateway razorpayGateway) {
		this.paymentGatewayOrderRepository = paymentGatewayOrderRepository;
		this.invoiceRepository = invoiceRepository;
		this.organisationRepository = organisationRepository;
		this.feeService = feeService;
		this.credentialsProvider = credentialsProvider;
		this.razorpayGateway = razorpayGateway;
	}

	/** Staff-facing - TenantContext is already set by JwtAuthenticationInterceptor. */
	@Transactional
	public IssuedLink createLink(Long invoiceId) {
		Invoice invoice = invoiceRepository.findById(invoiceId)
				.orElseThrow(() -> new IllegalArgumentException("No invoice with id " + invoiceId));
		if (invoice.getOutstanding().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalStateException("Invoice " + invoiceId + " has no outstanding balance to collect");
		}
		Organisation organisation = organisationRepository.findById(TenantContext.getOrganisationId())
				.orElseThrow(() -> new IllegalStateException("No organisation in context"));

		String token = generateToken();
		PaymentGatewayOrder order =
				paymentGatewayOrderRepository.save(new PaymentGatewayOrder(invoice, token, Instant.now().plus(LINK_VALIDITY)));
		return new IssuedLink(order.getId(), organisation.getSlug(), token, order.getExpiresAt());
	}

	/** Public, unauthenticated - same org-resolution shape as AuthenticationService.login()
	 * and PortalInviteService.claim(): the org slug is embedded in the URL staff shared
	 * (see createLink above), resolved first so TenantContext is set before any
	 * tenant-scoped repository call opens a session. */
	public LinkStatus getLinkStatus(String organisationSlug, String linkToken) {
		return withTenant(organisationSlug, () -> {
			PaymentGatewayOrder order = findOrderOrThrow(linkToken);
			Invoice invoice = order.getInvoice();
			return new LinkStatus(order.getStatus(), invoice.getInvoiceNumber(), invoice.getOutstanding(), order.getExpiresAt());
		});
	}

	/** Public, unauthenticated - creates (or re-quotes) the Razorpay order for this link,
	 * always against the invoice's current outstanding balance rather than whatever was
	 * true when the link was first generated. */
	public CheckoutSession initiateCheckout(String organisationSlug, String linkToken) {
		return withTenant(organisationSlug, () -> {
			PaymentGatewayOrder order = findOrderOrThrow(linkToken);
			if (order.getStatus() == PaymentGatewayOrderStatus.PAID) {
				throw new IllegalStateException("This invoice has already been paid");
			}
			if (order.getExpiresAt().isBefore(Instant.now())) {
				throw new IllegalStateException("This payment link has expired");
			}
			Invoice invoice = order.getInvoice();
			BigDecimal outstanding = invoice.getOutstanding();
			if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
				throw new IllegalStateException("This invoice has already been paid");
			}

			Organisation organisation = organisationRepository.findById(TenantContext.getOrganisationId())
					.orElseThrow(() -> new IllegalStateException("No organisation in context"));
			RazorpayCredentials credentials = credentialsProvider.resolveFor(organisation);
			long amountInPaise = outstanding.movePointRight(2).longValueExact();
			String gatewayOrderId = razorpayGateway.createOrder(credentials, amountInPaise, CURRENCY, invoice.getInvoiceNumber());

			order.attachGatewayOrder(gatewayOrderId, outstanding);
			paymentGatewayOrderRepository.save(order);

			return new CheckoutSession(gatewayOrderId, credentials.keyId(), amountInPaise, CURRENCY, invoice.getInvoiceNumber());
		});
	}

	/**
	 * Public, unauthenticated, and trusts nothing until the HMAC signature is verified -
	 * the only path in this whole feature that actually calls FeeService.recordPayment().
	 * There is no organisation to resolve from a slug here (the webhook carries only
	 * Razorpay's own order id), so this is the one deliberate exception to the
	 * resolve-tenant-from-a-slug pattern - see
	 * PaymentGatewayOrderRepository.findOrganisationIdByGatewayOrderId for why that one
	 * native query is safe. Idempotent: Razorpay may deliver the same event more than
	 * once, and PaymentGatewayOrder.markPaid() rejects a second attempt.
	 */
	public void handleWebhook(String rawBody, String signatureHeader, String eventType, String gatewayOrderId, String gatewayPaymentId) {
		RazorpayCredentials credentials = credentialsProvider.sharedCredentials();
		if (!razorpayGateway.verifyWebhookSignature(rawBody, signatureHeader, credentials.webhookSecret())) {
			throw new WebhookVerificationException("Invalid Razorpay webhook signature");
		}
		// Signature is checked above regardless of event type - never act on an unverified
		// body, not even to decide "ignore this." Only payment.captured ever records money.
		if (!"payment.captured".equals(eventType)) {
			return;
		}

		Long organisationId = paymentGatewayOrderRepository.findOrganisationIdByGatewayOrderId(gatewayOrderId)
				.orElseThrow(() -> new IllegalArgumentException("Unknown gateway order " + gatewayOrderId));
		TenantContext.set(organisationId, null);
		try {
			PaymentGatewayOrder order = paymentGatewayOrderRepository.findByGatewayOrderId(gatewayOrderId)
					.orElseThrow(() -> new IllegalArgumentException("Unknown gateway order " + gatewayOrderId));
			if (order.getStatus() == PaymentGatewayOrderStatus.PAID) {
				return;
			}

			Invoice invoice = order.getInvoice();
			AcademicYear academicYear = invoice.getAcademicYear();
			Payment payment = feeService.recordPayment(academicYear, order.getAmount(), PaymentMethod.ONLINE, LocalDate.now(),
					"Razorpay payment " + gatewayPaymentId, List.of(new FeeService.AllocationInput(invoice.getId(), order.getAmount())));

			order.markPaid(payment);
			paymentGatewayOrderRepository.save(order);
		} finally {
			TenantContext.clear();
		}
	}

	private <T> T withTenant(String organisationSlug, java.util.function.Supplier<T> action) {
		Organisation organisation = organisationRepository.findBySlug(organisationSlug)
				.orElseThrow(() -> new IllegalArgumentException("No organisation with slug " + organisationSlug));
		TenantContext.set(organisation.getId(), null);
		try {
			return action.get();
		} finally {
			TenantContext.clear();
		}
	}

	private PaymentGatewayOrder findOrderOrThrow(String linkToken) {
		return paymentGatewayOrderRepository.findByLinkToken(linkToken)
				.orElseThrow(() -> new IllegalArgumentException("No payment link with this token"));
	}

	private String generateToken() {
		byte[] bytes = new byte[24];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public record IssuedLink(Long orderId, String organisationSlug, String linkToken, Instant expiresAt) {
	}

	public record LinkStatus(PaymentGatewayOrderStatus status, String invoiceNumber, BigDecimal outstanding, Instant expiresAt) {
	}

	public record CheckoutSession(String gatewayOrderId, String razorpayKeyId, long amountInPaise, String currency, String invoiceNumber) {
	}
}
