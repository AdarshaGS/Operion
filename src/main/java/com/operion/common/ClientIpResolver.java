package com.operion.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Works out the caller's address for rate limiting on public endpoints. By default this is
 * {@code request.getRemoteAddr()} only: a client-supplied header such as X-Forwarded-For is trivially forged, so
 * trusting one without a proxy that overwrites it would let anyone dodge a limit by rotating the header value.
 *
 * <p>Behind a reverse proxy that sets a header, name it in {@code app.security.trusted-proxy-header} (for example
 * {@code X-Forwarded-For} or {@code X-Real-IP}). When that header carries a comma-separated list the right-most
 * entry is used - the one appended by the proxy closest to this app - so this assumes a single trusted proxy hop
 * that appends. Leave the property empty (the default) when the app is exposed directly.
 */
@Component
public class ClientIpResolver {

	private static final int MAX_ADDRESS_LENGTH = 64;

	private final String trustedProxyHeader;

	public ClientIpResolver(@Value("${app.security.trusted-proxy-header:}") String trustedProxyHeader) {
		this.trustedProxyHeader = trustedProxyHeader == null ? "" : trustedProxyHeader.trim();
	}

	public String resolve(HttpServletRequest request) {
		if (!trustedProxyHeader.isEmpty()) {
			String forwarded = lastEntry(request.getHeader(trustedProxyHeader));
			if (forwarded != null) {
				return forwarded;
			}
		}
		String remote = request.getRemoteAddr();
		return remote == null || remote.isBlank() ? "unknown" : remote;
	}

	private static String lastEntry(String headerValue) {
		if (headerValue == null) {
			return null;
		}
		String[] parts = headerValue.split(",");
		for (int i = parts.length - 1; i >= 0; i--) {
			String candidate = parts[i].trim();
			if (!candidate.isEmpty()) {
				return candidate.length() <= MAX_ADDRESS_LENGTH ? candidate : null;
			}
		}
		return null;
	}
}
