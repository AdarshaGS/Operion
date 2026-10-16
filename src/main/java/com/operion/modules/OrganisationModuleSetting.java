package com.operion.modules;

import com.operion.common.persistence.PlatformPlane;
import com.operion.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One row per toggle - insert-only history, the latest row per (organisation, module) is
 * the current state. Not tenant-scoped via @TenantId: it is written by the platform
 * admin plane, which has no organisation in its context, and read by the tenant plane
 * with an explicit organisation id (same convention as Subscription).
 */
@Getter
@Entity
@Table(name = "organisation_module_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@PlatformPlane
public class OrganisationModuleSetting extends BaseEntity {

	@Column(name = "organisation_id", nullable = false)
	private Long organisationId;

	@Enumerated(EnumType.STRING)
	@Column(name = "module_code", nullable = false, length = 40)
	private AppModule module;

	@Column(nullable = false)
	private boolean enabled;

	@Column(length = 255)
	private String note;

	public OrganisationModuleSetting(Long organisationId, AppModule module, boolean enabled, String note) {
		this.organisationId = organisationId;
		this.module = module;
		this.enabled = enabled;
		this.note = note;
	}
}
