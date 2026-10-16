package com.operion.common.persistence;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an entity or Spring Data repository as belonging to the Platform database
 * (organisations, plans/subscriptions/invoices, feature flags, platform admins, CRM,
 * support tickets, integration catalogue and grants, platform settings, reference data).
 * Everything without this annotation belongs to the School database, where every
 * tenant-owned table carries {@code organisation_id} (see {@link com.operion.common.TenantScopedEntity}).
 *
 * <p>The marker is read by {@link PersistenceConfig} to decide which EntityManagerFactory
 * manages an entity and which one backs a repository, and by the architecture tests to
 * keep JPA relationships from crossing the two databases - a foreign key cannot span them,
 * so a school entity refers to a platform row by plain id only.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PlatformPlane {
}
