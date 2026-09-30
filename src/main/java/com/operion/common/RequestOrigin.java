package com.operion.common;

/**
 * Remote address and User-Agent of the current request, read by AuditLogService so audit
 * rows can record where a change came from. Populated/cleared per request by
 * {@link RequestLoggingFilter} (a filter, not the JWT interceptor, because it must also
 * cover the public endpoints such as login). Deliberately separate from
 * {@link TenantContext}: this is request metadata, not tenancy or identity.
 */
public final class RequestOrigin {

	private record Origin(String ipAddress, String userAgent) {
	}

	private static final ThreadLocal<Origin> CURRENT = new ThreadLocal<>();

	private RequestOrigin() {
	}

	public static void set(String ipAddress, String userAgent) {
		CURRENT.set(new Origin(ipAddress, userAgent));
	}

	public static String getIpAddress() {
		Origin origin = CURRENT.get();
		return origin == null ? null : origin.ipAddress();
	}

	public static String getUserAgent() {
		Origin origin = CURRENT.get();
		return origin == null ? null : origin.userAgent();
	}

	public static void clear() {
		CURRENT.remove();
	}
}
