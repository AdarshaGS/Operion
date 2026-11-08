package com.operion.finance.api;

import java.time.Duration;
import java.util.function.Supplier;

import com.operion.authorization.RequirePermission;
import com.operion.common.ClientIpResolver;
import com.operion.common.SlidingWindowRateLimiter;
import com.operion.common.TooManyRequestsException;
import com.operion.finance.FeePaymentGatewayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff-initiated "pay this invoice" links - the public, unauthenticated pieces
 * (get status, checkout) live under a class-level @RequirePermission-free mapping
 * exception below, since a parent opening the link has no token at all.
 */
@RestController
@RequestMapping("/api/v1/fees")
public class PaymentLinkController {

	/** One answer for "no such school" and "no such / unusable link", so the response never says which part was wrong. */
	static final String UNKNOWN_LINK_MESSAGE = "No such payment link";
	private static final int MAX_SEGMENT_LENGTH = 128;

	private final FeePaymentGatewayService gatewayService;
	private final ClientIpResolver clientIpResolver;
	/** Everything one address sends to these endpoints, whatever the token - stops token guessing by spraying. */
	private final SlidingWindowRateLimiter perAddressLimiter = new SlidingWindowRateLimiter(120, Duration.ofMinutes(10), 10_000);
	/** Repeated lookups of one link from one address. */
	private final SlidingWindowRateLimiter statusLimiter = new SlidingWindowRateLimiter(30, Duration.ofMinutes(10), 10_000);
	/** Checkout creates a gateway order, so it is held tighter than the status read. */
	private final SlidingWindowRateLimiter checkoutLimiter = new SlidingWindowRateLimiter(10, Duration.ofMinutes(10), 10_000);

	public PaymentLinkController(FeePaymentGatewayService gatewayService, ClientIpResolver clientIpResolver) {
		this.gatewayService = gatewayService;
		this.clientIpResolver = clientIpResolver;
	}

	@PostMapping("/invoices/{invoiceId}/payment-link")
	@RequirePermission("FEE_COLLECT")
	public PaymentLinkResponse createLink(@PathVariable Long invoiceId) {
		return PaymentLinkResponse.from(gatewayService.createLink(invoiceId));
	}

	// Public - registered in JwtAuthenticationInterceptor's allowlist, since a parent
	// opening this link has no bearer token at all. The org slug travels in the path
	// (embedded in the link staff shares), same shape as claim-invite carrying it in the
	// request body - there's no token yet either way to resolve it from.
	@GetMapping("/payment-links/{organisationSlug}/{linkToken}")
	public PaymentLinkStatusResponse status(@PathVariable String organisationSlug, @PathVariable String linkToken,
			HttpServletRequest httpRequest) {
		return guarded(statusLimiter, organisationSlug, linkToken, httpRequest,
				() -> PaymentLinkStatusResponse.from(gatewayService.getLinkStatus(organisationSlug, linkToken)));
	}

	@PostMapping("/payment-links/{organisationSlug}/{linkToken}/checkout")
	public CheckoutSessionResponse checkout(@PathVariable String organisationSlug, @PathVariable String linkToken,
			HttpServletRequest httpRequest) {
		return guarded(checkoutLimiter, organisationSlug, linkToken, httpRequest,
				() -> CheckoutSessionResponse.from(gatewayService.initiateCheckout(organisationSlug, linkToken)));
	}

	/**
	 * Rate-limits per address and per address+link, then maps the service's "no such organisation" and "no such link"
	 * failures (both {@link IllegalArgumentException}) to one identical 404. An expired or already-paid link is a
	 * different failure ({@link IllegalStateException}) and is left to surface as before: it is only reachable by
	 * someone who already holds a valid token.
	 */
	private <T> T guarded(SlidingWindowRateLimiter limiter, String organisationSlug, String linkToken,
			HttpServletRequest httpRequest, Supplier<T> action) {
		if (organisationSlug.length() > MAX_SEGMENT_LENGTH || linkToken.length() > MAX_SEGMENT_LENGTH) {
			throw new IllegalArgumentException(UNKNOWN_LINK_MESSAGE);
		}
		String address = clientIpResolver.resolve(httpRequest);
		if (!perAddressLimiter.tryAcquire(address)
				|| !limiter.tryAcquire(address + "|" + organisationSlug + "|" + linkToken)) {
			throw new TooManyRequestsException("Too many requests. Please try again later.");
		}
		try {
			return action.get();
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(UNKNOWN_LINK_MESSAGE);
		}
	}
}
