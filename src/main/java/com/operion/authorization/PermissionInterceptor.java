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

		// The Owner's membership is the org's "*" capability - bypassing the granular check
		// entirely (rather than enumerating every catalog code onto their role) means a
		// permission added to the catalog tomorrow automatically covers every existing
		// Owner with no data backfill.
		if (membershipRepository.existsByUserIdAndStatusAndOwner(actorId, MembershipStatus.ACTIVE, true)) {
			return true;
		}

		// ALL_FUNCTIONS (GitHub #200) is the assignable-to-any-role equivalent of the Owner
		// bypass above - lets an org grant a *custom* role blanket admin access via the
		// normal Role -> Permission chain, without needing to be the fixed Owner membership.
		Set<String> grantedCodes = membershipRepository.findActivePermissionCodesForUser(actorId);
		if (grantedCodes.contains("ALL_FUNCTIONS")) {
			return true;
		}
		if (!grantedCodes.contains(required.value())) {
			throw new AuthorizationDeniedException("Missing required permission: " + required.value());
		}
		return true;
	}
}
