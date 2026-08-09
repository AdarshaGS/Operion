package com.operion.common;

import com.operion.identity.auth.JwtAuthenticationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final JwtAuthenticationInterceptor jwtAuthenticationInterceptor;

	public WebConfig(JwtAuthenticationInterceptor jwtAuthenticationInterceptor) {
		this.jwtAuthenticationInterceptor = jwtAuthenticationInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(jwtAuthenticationInterceptor).addPathPatterns("/api/v1/**");
	}
}
