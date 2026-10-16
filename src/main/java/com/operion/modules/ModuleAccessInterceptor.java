package com.operion.modules;

import java.lang.reflect.Method;

import com.operion.common.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Second gate behind permissions: even a caller who holds the permission - including the
 * Owner, who bypasses every permission check - gets a 403 for an endpoint whose module the
 * platform owner switched off for their organisation. Registered after
 * JwtAuthenticationInterceptor (needs TenantContext) and before PermissionInterceptor, so
 * the answer for a disabled module is the same whatever the caller's role. Data is never
 * touched; switching the module back on restores access as it was.
 */
@Component
public class ModuleAccessInterceptor implements HandlerInterceptor {

	private final ModuleAccessService moduleAccessService;

	public ModuleAccessInterceptor(ModuleAccessService moduleAccessService) {
		this.moduleAccessService = moduleAccessService;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (HttpMethod.OPTIONS.matches(request.getMethod()) || !(handler instanceof HandlerMethod handlerMethod)) {
			return true;
		}
		Long organisationId = TenantContext.getOrganisationId();
		if (organisationId == null) {
			return true;
		}
		AppModule module = resolve(handlerMethod.getMethod(), handlerMethod.getBeanType());
		if (module != null && !moduleAccessService.isEnabled(organisationId, module)) {
			throw new ModuleDisabledException(module);
		}
		return true;
	}

	/** Method annotation, then class annotation, then the controller's package; null = core, never gated. */
	static AppModule resolve(Method method, Class<?> beanType) {
		RequireModule onMethod = AnnotatedElementUtils.findMergedAnnotation(method, RequireModule.class);
		if (onMethod != null) {
			return onMethod.value();
		}
		Class<?> controller = ClassUtils.getUserClass(beanType);
		RequireModule onClass = AnnotatedElementUtils.findMergedAnnotation(controller, RequireModule.class);
		if (onClass != null) {
			return onClass.value();
		}
		return AppModule.forPackage(controller.getPackageName()).orElse(null);
	}
}
