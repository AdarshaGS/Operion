package com.operion.common;

import com.operion.identity.auth.JwtAuthenticationInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final JwtAuthenticationInterceptor jwtAuthenticationInterceptor;

	/** Defaults to the Vite dev server - the React admin portal calls the API directly, no BFF, per erp-system-plan.md §4. */
	@Value("${app.cors.allowed-origins:http://localhost:5173}")
	private String[] allowedOrigins;

	public WebConfig(JwtAuthenticationInterceptor jwtAuthenticationInterceptor) {
		this.jwtAuthenticationInterceptor = jwtAuthenticationInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(jwtAuthenticationInterceptor).addPathPatterns("/api/v1/**");
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/v1/**")
				.allowedOrigins(allowedOrigins)
				.allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE")
				.allowedHeaders("*");
	}
}
