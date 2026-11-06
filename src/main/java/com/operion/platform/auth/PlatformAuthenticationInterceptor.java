package com.operion.platform.auth;

import com.operion.common.TenantContext;
import com.operion.platform.PlatformAdminRepository;
import com.operion.platform.PlatformAdminStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Mounted only on /api/v1/platform/** (see WebConfig), which is excluded from
 * JwtAuthenticationInterceptor/PermissionInterceptor - the two auth planes never run on
 * the same path, so an org-scoped token can't reach a platform endpoint and vice versa.
 *
 * Reuses TenantContext's actor slot purely for created_by/updated_by auditing
 * (JpaConfig's AuditorAware reads TenantContext.getActorId()); organisationId is left
 * null, which MultiTenancyConfig resolves to its NO_TENANT sentinel - harmless, since no
 * entity reachable on this path carries @TenantId.
 */
@Component
public class PlatformAuthenticationInterceptor implements HandlerInterceptor {

	private static final String BEARER_PREFIX = "Bearer ";

	private final PlatformJwtService platformJwtService;
	private final PlatformAdminRepository platformAdminRepository;

	public PlatformAuthenticationInterceptor(PlatformJwtService platformJwtService, PlatformAdminRepository platformAdminRepository) {
		this.platformJwtService = platformJwtService;
		this.platformAdminRepository = platformAdminRepository;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (HttpMethod.OPTIONS.matches(request.getMethod()) || isPublic(request)) {
			return true;
		}

		String header = request.getHeader("Authorization");
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return false;
		}

		Long platformAdminId;
		try {
			platformAdminId = platformJwtService.decodeToPlatformAdminId(header.substring(BEARER_PREFIX.length()));
		} catch (InvalidPlatformTokenException ex) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return false;
		}
		// A valid signature only proves the admin was active when the token was issued. Re-check on every request so
		// disabling (or deleting) an admin cuts off their console access at once, not when the token expires.
		boolean active = platformAdminRepository.findById(platformAdminId)
				.map(admin -> admin.getStatus() == PlatformAdminStatus.ACTIVE)
				.orElse(false);
		if (!active) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return false;
		}
		TenantContext.set(null, platformAdminId);
		return true;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
		TenantContext.clear();
	}

	// Bootstrap: there is no public registration endpoint for this identity plane at all
	// (see V25's seed migration comment) - only login itself is unauthenticated.
	private boolean isPublic(HttpServletRequest request) {
		return request.getRequestURI().equals("/api/v1/platform/auth/login");
	}
}
