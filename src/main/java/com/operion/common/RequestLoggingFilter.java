package com.operion.common;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs for every request (filters wrap the whole dispatch, unlike interceptors which only
 * run inside it), so this is the one place that can log a single summary line per request
 * - request ID, method, path, actor, status, duration - regardless of which controller or
 * whether it was even routed. The actor comes from a request attribute JwtAuthenticationInterceptor
 * stashes in afterCompletion, not TenantContext directly: by the time this filter's
 * post-chain code runs, TenantContext.clear() has already fired.
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger("com.operion.request");
	private static final String REQUEST_ID_HEADER = "X-Request-Id";
	private static final String MDC_KEY = "requestId";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String requestId = request.getHeader(REQUEST_ID_HEADER);
		if (requestId == null || requestId.isBlank()) {
			requestId = UUID.randomUUID().toString();
		}
		response.setHeader(REQUEST_ID_HEADER, requestId);
		MDC.put(MDC_KEY, requestId);

		long start = System.currentTimeMillis();
		try {
			filterChain.doFilter(request, response);
		} finally {
			long durationMs = System.currentTimeMillis() - start;
			Object actorUserId = request.getAttribute("operion.actorUserId");
			log.info("{} {} user={} status={} durationMs={}", request.getMethod(), request.getRequestURI(),
					actorUserId != null ? actorUserId : "-", response.getStatus(), durationMs);
			MDC.remove(MDC_KEY);
		}
	}
}
