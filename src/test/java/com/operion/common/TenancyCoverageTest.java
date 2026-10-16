package com.operion.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.operion.common.persistence.PlatformPlane;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * Every entity is school-owned and filtered per organisation (TenantScopedEntity), or
 * deliberately something else. The "something else" cases are listed, each with its reason,
 * so a new entity that is neither fails here and a table of school data can never ship without
 * its organisation_id.
 */
class TenancyCoverageTest {

	/** Shared reference data seeded by migration, identical for every organisation. */
	private static final Set<String> GLOBAL_CATALOGS = Set.of("Permission", "SchoolTemplateMasterItem", "SchoolStarterNeed");

	/** Carry their own organisation_id but are read and written by explicit id, not auto-filtered:
	 * audit and login rows exist before/outside a tenant context, and the rest are one-per-organisation settings. */
	private static final Set<String> EXPLICIT_ORGANISATION_ID = Set.of("AcademicConfiguration", "AuditLog", "LoginAttempt",
			"OrganisationBranding", "OrganisationConfiguration");

	@Test
	void everyEntityIsTenantScopedOrDeliberatelyPlatformLevel() throws Exception {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
		List<String> unscoped = new ArrayList<>();
		for (BeanDefinition definition : scanner.findCandidateComponents("com.operion")) {
			Class<?> entity = Class.forName(definition.getBeanClassName());
			boolean tenantScoped = TenantScopedEntity.class.isAssignableFrom(entity);
			boolean platform = entity.isAnnotationPresent(PlatformPlane.class);
			boolean global = GLOBAL_CATALOGS.contains(entity.getSimpleName());
			boolean explicit = EXPLICIT_ORGANISATION_ID.contains(entity.getSimpleName()) && hasOrganisationIdField(entity);
			if (!tenantScoped && !platform && !global && !explicit) {
				unscoped.add(entity.getSimpleName());
			}
		}
		assertThat(unscoped).as("entities that are not tenant-scoped, platform-level, a global catalog or explicitly keyed by organisation_id").isEmpty();
	}

	private static boolean hasOrganisationIdField(Class<?> entity) {
		try {
			entity.getDeclaredField("organisationId");
			return true;
		} catch (NoSuchFieldException e) {
			return false;
		}
	}
}
