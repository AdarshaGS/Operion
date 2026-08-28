package com.operion.platform.auth;

/**
 * Reserves the platform plane's own wildcard/bypass name (GitHub #200), kept distinct from
 * the org-scoped {@code ALL_FUNCTIONS} permission (see {@code com.operion.authorization.
 * PermissionInterceptor}) so the two never collide if this plane ever grows granular
 * permissions of its own.
 *
 * Not enforced anywhere yet - {@link PlatformAuthenticationInterceptor} only checks "is
 * this a valid PlatformAdmin token," with no granular permission catalog on this plane at
 * all, so every platform admin already has unconditional full access. This constant exists
 * purely as a naming reservation for whenever that changes.
 */
public final class PlatformPermissions {

	public static final String ALL_PLATFORM_FUNCTIONS = "ALL_PLATFORM_FUNCTIONS";

	private PlatformPermissions() {
	}
}
