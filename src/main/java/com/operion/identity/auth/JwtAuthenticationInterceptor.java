package com.operion.identity.auth;

import com.operion.common.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Resolves the acting user + organisation from the request's bearer token and
 * populates TenantContext for the duration of the request - the seam every
 * tenant-scoped endpoint needs, flagged as missing in ai-context/erp-system-plan.md §1.6.
 */
@Component
public class JwtAuthenticationInterceptor implements HandlerInterceptor {

	private static final String BEARER_PREFIX = "Bearer ";
	private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationInterceptor.class);

	private final JwtService jwtService;

	public JwtAuthenticationInterceptor(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		// A CORS preflight never carries the real bearer token - Spring's own CORS
		// processing (WebConfig.addCorsMappings) already validated the origin/method/
		// headers before this interceptor runs, so let it through to be answered as a
		// preflight rather than rejecting it here and breaking every cross-origin call.
		if (HttpMethod.OPTIONS.matches(request.getMethod()) || isPublic(request)) {
			return true;
		}

		String header = request.getHeader("Authorization");
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			log.warn("Rejected request with no bearer token: {} {}", request.getMethod(), request.getRequestURI());
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return false;
		}

		try {
			JwtService.TokenPrincipal principal = jwtService.decodeToPrincipal(header.substring(BEARER_PREFIX.length()));
			TenantContext.set(principal.organisationId(), principal.userId());
			return true;
		} catch (InvalidTokenException ex) {
			log.warn("Rejected request with invalid/expired token: {} {} ({})", request.getMethod(), request.getRequestURI(), ex.getMessage());
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return false;
		}
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
		// Stashed as a request attribute (not read straight from TenantContext) because
		// RequestLoggingFilter's own post-chain code runs after this method - by then
		// TenantContext.clear() below has already wiped the ThreadLocal.
		request.setAttribute("operion.actorUserId", TenantContext.getActorId());
		TenantContext.clear();
	}

	// Organisation creation is the bootstrap step - there is no token to send before the
	// org (and its first admin login) exists yet, so it alone stays unauthenticated.
	// claim-invite is the same shape of bootstrap for a Guardian: PortalInviteService
	// resolves the org from the request body's slug itself, exactly like login() does,
	// since there is no token yet to carry it. claim-staff-invite is the identical shape for
	// StaffInviteService. password-reset/request+confirm and verify-email are the same
	// again - a caller who forgot their password or is confirming their email by definition
	// has no valid access token either. Payment links are the same shape again, but with the
	// org slug carried in the URL path instead (see
	// FeePaymentGatewayService.getLinkStatus/initiateCheckout) - a parent opening a
	// "pay this invoice" link has no login at all, by design. The Razorpay webhook is a
	// different kind of public: it trusts an HMAC signature instead of a slug+token,
	// verified inside FeePaymentGatewayService.handleWebhook - never a bearer token,
	// since the caller is Razorpay's own servers, not a browser. The three
	// /api/v1/webhooks/{resend,brevo}/... paths are the same shape of public, trusted
	// only after NotificationDeliveryWebhookService verifies each provider's own
	// signature/secret - see that class's doc.
	private boolean isPublic(HttpServletRequest request) {
		String path = request.getRequestURI();
		if (path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/claim-invite") || path.equals("/api/v1/auth/refresh")
				|| path.equals("/api/v1/auth/claim-staff-invite") || path.equals("/api/v1/auth/password-reset/request")
				|| path.equals("/api/v1/auth/password-reset/confirm") || path.equals("/api/v1/auth/verify-email")) {
			return true;
		}
		if (path.startsWith("/api/v1/fees/payment-links/") || path.startsWith("/api/v1/webhooks/")) {
			return true;
		}
		return path.equals("/api/v1/organisations") && "POST".equalsIgnoreCase(request.getMethod());
	}
}
