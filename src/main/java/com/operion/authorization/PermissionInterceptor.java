package com.operion.authorization;

import java.util.Set;

import com.operion.common.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Runs after {@link com.operion.identity.auth.JwtAuthenticationInterceptor} (registration
 * order in WebConfig) so TenantContext is already populated. An endpoint with no
 * {@link RequirePermission} annotation (method or class level) is reachable by any
 * authenticated org member - see that annotation's javadoc for why that's the deliberate
 * default rather than requiring every lookup/picker endpoint to be explicitly annotated.
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

	private final OrganisationMembershipRepository membershipRepository;

	public PermissionInterceptor(OrganisationMembershipRepository membershipRepository) {
		this.membershipRepository = membershipRepository;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (HttpMethod.OPTIONS.matches(request.getMethod()) || !(handler instanceof HandlerMethod handlerMethod)) {
			return true;
		}

		RequirePermission required = handlerMethod.getMethodAnnotation(RequirePermission.class);
		if (required == null) {
			required = handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
		}
		if (required == null) {
			return true;
		}

		Long actorId = TenantContext.getActorId();
		if (actorId == null) {
			throw new AuthorizationDeniedException("No authenticated caller for a permission-gated endpoint");
		}

		Set<String> grantedCodes = membershipRepository.findActivePermissionCodesForUser(actorId);
		if (!grantedCodes.contains(required.value())) {
			throw new AuthorizationDeniedException("Missing required permission: " + required.value());
		}
		return true;
	}
}
