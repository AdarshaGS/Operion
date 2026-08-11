package com.operion.common;

import com.operion.authorization.PermissionInterceptor;
import com.operion.identity.auth.JwtAuthenticationInterceptor;
import com.operion.platform.auth.PlatformAuthenticationInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	private static final String PLATFORM_PATH_PATTERN = "/api/v1/platform/**";

	private final JwtAuthenticationInterceptor jwtAuthenticationInterceptor;
	private final PermissionInterceptor permissionInterceptor;
	private final PlatformAuthenticationInterceptor platformAuthenticationInterceptor;

	/** Defaults to the Vite dev server - the React admin portal calls the API directly, no BFF, per erp-system-plan.md §4. */
	@Value("${app.cors.allowed-origins:http://localhost:5173}")
	private String[] allowedOrigins;

	public WebConfig(JwtAuthenticationInterceptor jwtAuthenticationInterceptor, PermissionInterceptor permissionInterceptor,
			PlatformAuthenticationInterceptor platformAuthenticationInterceptor) {
		this.jwtAuthenticationInterceptor = jwtAuthenticationInterceptor;
		this.permissionInterceptor = permissionInterceptor;
		this.platformAuthenticationInterceptor = platformAuthenticationInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		// Permission checks must run after the JWT interceptor populates TenantContext -
		// registration order is execution order for Spring MVC interceptors. The
		// platform-admin plane is a completely separate auth mechanism (its own JWT
		// secret, no OrganisationMembership/Role/Permission involved) - excluded here and
		// gated by its own interceptor instead, so an org-scoped token can never reach a
		// platform endpoint and a platform token can never reach an org-scoped one.
		registry.addInterceptor(jwtAuthenticationInterceptor).addPathPatterns("/api/v1/**").excludePathPatterns(PLATFORM_PATH_PATTERN);
		registry.addInterceptor(permissionInterceptor).addPathPatterns("/api/v1/**").excludePathPatterns(PLATFORM_PATH_PATTERN);
		registry.addInterceptor(platformAuthenticationInterceptor).addPathPatterns(PLATFORM_PATH_PATTERN);
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/v1/**")
				.allowedOrigins(allowedOrigins)
				.allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE")
				.allowedHeaders("*");
	}
}
