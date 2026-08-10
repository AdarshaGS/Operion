package com.operion.identity.auth;

import com.operion.common.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return false;
		}

		try {
			JwtService.TokenPrincipal principal = jwtService.decodeToPrincipal(header.substring(BEARER_PREFIX.length()));
			TenantContext.set(principal.organisationId(), principal.userId());
			return true;
		} catch (InvalidTokenException ex) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return false;
		}
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
		TenantContext.clear();
	}

	// Organisation creation is the bootstrap step - there is no token to send before the
	// org (and its first admin login) exists yet, so it alone stays unauthenticated.
	private boolean isPublic(HttpServletRequest request) {
		String path = request.getRequestURI();
		if (path.equals("/api/v1/auth/login")) {
			return true;
		}
		return path.equals("/api/v1/organisations") && "POST".equalsIgnoreCase(request.getMethod());
	}
}
