package com.operion.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the permission code a caller's organisation membership(s) must include to
 * invoke this endpoint. Read by {@link PermissionInterceptor}. A method-level annotation
 * overrides a class-level one; an endpoint with neither is reachable by any authenticated
 * member of the organisation (no specific permission required) - the deliberate default
 * for read-only lookup/picker endpoints consumed broadly across roles (e.g. listing
 * campuses to populate a dropdown).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequirePermission {

	String value();
}
