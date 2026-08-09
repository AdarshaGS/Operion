package com.operion.common;

/**
 * Holds the current request's organisation (tenant) and acting user, read by
 * {@link MultiTenancyConfig}'s resolver and by JPA auditing for created_by/updated_by.
 *
 * Nothing populates this yet — no auth layer exists. Once one does, its filter/interceptor
 * must call {@link #set} at the start of each request and {@link #clear} at the end
 * (e.g. in a {@code finally} block) to avoid leaking a tenant across pooled threads.
 */
public final class TenantContext {

	private static final ThreadLocal<Long> CURRENT_ORGANISATION_ID = new ThreadLocal<>();
	private static final ThreadLocal<Long> CURRENT_ACTOR_ID = new ThreadLocal<>();

	private TenantContext() {
	}

	public static void set(Long organisationId, Long actorId) {
		CURRENT_ORGANISATION_ID.set(organisationId);
		CURRENT_ACTOR_ID.set(actorId);
	}

	public static Long getOrganisationId() {
		return CURRENT_ORGANISATION_ID.get();
	}

	public static Long getActorId() {
		return CURRENT_ACTOR_ID.get();
	}

	public static void clear() {
		CURRENT_ORGANISATION_ID.remove();
		CURRENT_ACTOR_ID.remove();
	}
}
