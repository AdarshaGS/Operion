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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
	private static final Logger log = LoggerFactory.getLogger(FeePaymentGatewayService.class);

	private final PaymentGatewayOrderRepository paymentGatewayOrderRepository;
	private final PaymentGatewayAttemptRepository paymentGatewayAttemptRepository;
	private final InvoiceRepository invoiceRepository;
	private final OrganisationRepository organisationRepository;
	private final FeeService feeService;
	private final RazorpayCredentialsProvider credentialsProvider;
	private final RazorpayGateway razorpayGateway;
	/** Not @Transactional on the public methods: the tenant must be set BEFORE the session opens (see
	 * withTenant), and a self-invoked @Transactional method would not be proxied anyway - so the
	 * tenant-scoped work runs inside an explicit template after TenantContext is set. */
	private final TransactionTemplate transactionTemplate;
	private final SecureRandom secureRandom = new SecureRandom();

	public FeePaymentGatewayService(PaymentGatewayOrderRepository paymentGatewayOrderRepository,
			PaymentGatewayAttemptRepository paymentGatewayAttemptRepository, InvoiceRepository invoiceRepository,
			OrganisationRepository organisationRepository, FeeService feeService, RazorpayCredentialsProvider credentialsProvider,
			RazorpayGateway razorpayGateway, PlatformTransactionManager transactionManager) {
		this.paymentGatewayOrderRepository = paymentGatewayOrderRepository;
		this.paymentGatewayAttemptRepository = paymentGatewayAttemptRepository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
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

	/** Public, unauthenticated - returns the Razorpay order for this link, always quoted against the
	 * invoice's current outstanding balance rather than whatever was true when the link was first
	 * generated. If the link already has a Razorpay order for exactly this amount it is reused (a
	 * second tab or a page refresh must not mint a new order a parent might pay against while the
	 * first one is the one the link points at). If the outstanding changed, a new order is created and
	 * recorded as an additional PaymentGatewayAttempt - earlier orders stay resolvable by the webhook,
	 * since a parent may still pay against one of them. */
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

			if (order.getGatewayOrderId() != null && order.getAmount() != null && order.getAmount().compareTo(outstanding) == 0) {
				return new CheckoutSession(order.getGatewayOrderId(), credentials.keyId(), amountInPaise, CURRENCY, invoice.getInvoiceNumber());
			}

			// The gateway call stays outside any database transaction - never hold a row lock across HTTP.
			String gatewayOrderId = razorpayGateway.createOrder(credentials, amountInPaise, CURRENCY, invoice.getInvoiceNumber());
			transactionTemplate.executeWithoutResult(status -> {
				PaymentGatewayOrder locked = paymentGatewayOrderRepository.findByIdForUpdate(order.getId())
						.orElseThrow(() -> new IllegalStateException("Payment link vanished during checkout"));
				if (locked.getStatus() == PaymentGatewayOrderStatus.PAID) {
					throw new IllegalStateException("This invoice has already been paid");
				}
				locked.attachGatewayOrder(gatewayOrderId, outstanding);
				paymentGatewayOrderRepository.save(locked);
				paymentGatewayAttemptRepository.save(new PaymentGatewayAttempt(locked, gatewayOrderId, outstanding));
			});

			return new CheckoutSession(gatewayOrderId, credentials.keyId(), amountInPaise, CURRENCY, invoice.getInvoiceNumber());
		});
	}

	/**
	 * Public, unauthenticated, and trusts nothing until the HMAC signature is verified -
	 * the only path in this whole feature that actually calls FeeService.recordPayment().
	 * There is no organisation to resolve from a slug here (the webhook carries only
	 * Razorpay's own order id), so this is the one deliberate exception to the
	 * resolve-tenant-from-a-slug pattern - see
	 * PaymentGatewayAttemptRepository.findOrganisationIdByGatewayOrderId for why that one
	 * native query is safe.
	 *
	 * <p>Crediting is one database transaction (recordPayment + marking the link PAID commit or roll
	 * back together, so a failure can never leave money recorded against a link that still looks
	 * unpaid and would be credited again on Razorpay's retry), opened only after TenantContext is
	 * set. The link row is locked (SELECT ... FOR UPDATE) for its duration, so concurrent
	 * redeliveries of one payment.captured serialize and the second sees PAID. Idempotent on the
	 * Razorpay payment id: the same id arriving again is a no-op; the id is also unique in the
	 * database.
	 *
	 * <p>What is credited is the amount quoted on the Razorpay order the payment was made against
	 * (PaymentGatewayAttempt), and only if the verified payload's captured amount (paise) and
	 * currency equal it exactly. Every situation that cannot be credited safely - amount/currency
	 * mismatch, unknown gateway order, a second payment against an already-paid link, a payment
	 * larger than what is still owed - is logged at ERROR with the ids needed for manual
	 * reconciliation (refund or record by hand), credits nothing, and RETURNS NORMALLY (HTTP 2xx).
	 * Reason: Razorpay retries any non-2xx for about 24 hours and may disable a webhook that keeps
	 * failing, none of these conditions can fix themselves on retry, and the account is shared
	 * across the platform, so an order id we have never seen may legitimately be someone else's.
	 * A signature failure still throws (401), and so does any unexpected failure while crediting
	 * (the transaction rolls back, so a retry is safe and desirable).
	 */
	public void handleWebhook(String rawBody, String signatureHeader, String eventType, String gatewayOrderId, String gatewayPaymentId,
			Long capturedAmountInPaise, String capturedCurrency) {
		RazorpayCredentials credentials = credentialsProvider.sharedCredentials();
		if (!razorpayGateway.verifyWebhookSignature(rawBody, signatureHeader, credentials.webhookSecret())) {
			throw new WebhookVerificationException("Invalid Razorpay webhook signature");
		}
		// Signature is checked above regardless of event type - never act on an unverified
		// body, not even to decide "ignore this." Only payment.captured ever records money.
		if (!"payment.captured".equals(eventType)) {
			return;
		}
		if (gatewayOrderId == null || gatewayOrderId.isBlank() || gatewayPaymentId == null || gatewayPaymentId.isBlank()) {
			log.error("Razorpay payment.captured without an order id or payment id - NOT credited, reconcile manually: orderId={} paymentId={}",
					gatewayOrderId, gatewayPaymentId);
			return;
		}

		Long organisationId = paymentGatewayAttemptRepository.findOrganisationIdByGatewayOrderId(gatewayOrderId).orElse(null);
		if (organisationId == null) {
			log.error("Razorpay payment.captured for an unknown gateway order - NOT credited, reconcile manually: orderId={} paymentId={} amountInPaise={}",
					gatewayOrderId, gatewayPaymentId, capturedAmountInPaise);
			return;
		}
		TenantContext.set(organisationId, null);
		try {
			transactionTemplate.executeWithoutResult(
					status -> creditCapturedPayment(gatewayOrderId, gatewayPaymentId, capturedAmountInPaise, capturedCurrency));
		} finally {
			TenantContext.clear();
		}
	}

	/** Runs inside handleWebhook's transaction, TenantContext already set. */
	private void creditCapturedPayment(String gatewayOrderId, String gatewayPaymentId, Long capturedAmountInPaise, String capturedCurrency) {
		PaymentGatewayAttempt attempt = paymentGatewayAttemptRepository.findByGatewayOrderId(gatewayOrderId).orElse(null);
		if (attempt == null) {
			log.error("Razorpay payment.captured: gateway order resolved to an organisation but not inside it - NOT credited, reconcile manually: "
					+ "orderId={} paymentId={}", gatewayOrderId, gatewayPaymentId);
			return;
		}
		PaymentGatewayOrder order = paymentGatewayOrderRepository.findByIdForUpdate(attempt.getOrder().getId())
				.orElseThrow(() -> new IllegalStateException("Payment link missing for gateway order " + gatewayOrderId));

		if (order.getStatus() == PaymentGatewayOrderStatus.PAID) {
			if (gatewayPaymentId.equals(order.getGatewayPaymentId())) {
				log.info("Razorpay payment {} already credited to payment link {} - duplicate delivery ignored", gatewayPaymentId, order.getId());
			} else {
				log.error("Razorpay payment {} (order {}) captured against payment link {} that was already paid by payment {} - NOT credited, "
						+ "refund or reconcile manually", gatewayPaymentId, gatewayOrderId, order.getId(), order.getGatewayPaymentId());
			}
			return;
		}

		BigDecimal quoted = attempt.getAmount();
		long quotedInPaise = quoted.movePointRight(2).longValueExact();
		if (capturedAmountInPaise == null || capturedAmountInPaise != quotedInPaise || !CURRENCY.equals(capturedCurrency)) {
			log.error("Razorpay captured amount does not match the quoted amount - NOT credited, reconcile manually: orderId={} paymentId={} "
					+ "paymentLinkId={} quotedPaise={} quotedCurrency={} capturedPaise={} capturedCurrency={}",
					gatewayOrderId, gatewayPaymentId, order.getId(), quotedInPaise, CURRENCY, capturedAmountInPaise, capturedCurrency);
			return;
		}

		Invoice invoice = order.getInvoice();
		if (quoted.compareTo(invoice.getOutstanding()) > 0) {
			// Staff recorded another payment between checkout and capture; FeeService would reject the
			// over-allocation, and failing here would only make Razorpay retry a call that cannot succeed.
			log.error("Razorpay payment {} (order {}) of {} exceeds invoice {}'s remaining outstanding {} - NOT credited, refund or reconcile manually",
					gatewayPaymentId, gatewayOrderId, quoted, invoice.getId(), invoice.getOutstanding());
			return;
		}

		AcademicYear academicYear = invoice.getAcademicYear();
		Payment payment = feeService.recordPayment(academicYear, quoted, PaymentMethod.ONLINE, LocalDate.now(),
				"Razorpay payment " + gatewayPaymentId, List.of(new FeeService.AllocationInput(invoice.getId(), quoted)), gatewayPaymentId);
		order.markPaid(payment, gatewayPaymentId);
		paymentGatewayOrderRepository.save(order);
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
