package com.operion.onboarding;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.operion.common.BaseEntity;
import com.operion.modules.AppModule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * One question the School Starter Pack asks at onboarding, and the modules a "yes" turns on.
 * Seeded by {@code V132__school_starter_pack_modules.sql}, same convention as
 * {@link SchoolTemplateMasterItem}: a shared catalog changed by migration, never written by
 * the app and never hardcoded here. A baseline row is not asked - its modules are always on.
 */
@Getter
@Entity
@Table(name = "school_starter_needs")
public class SchoolStarterNeed extends BaseEntity {

	@Column(name = "need_key", nullable = false, length = 40)
	private String needKey;

	@Column(nullable = false, length = 200)
	private String label;

	@Column(name = "help_text", length = 500)
	private String helpText;

	@Column(name = "default_yes", nullable = false)
	private boolean defaultYes;

	@Column(nullable = false)
	private boolean baseline;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	@Column(name = "module_codes", nullable = false, length = 200)
	private String moduleCodes;

	protected SchoolStarterNeed() {
	}

	/** Public for test fixtures only (Flyway is off under the H2 test profile); production rows come from V132. */
	public SchoolStarterNeed(String needKey, String label, String helpText, boolean defaultYes, boolean baseline, int displayOrder,
			String moduleCodes) {
		this.needKey = needKey;
		this.label = label;
		this.helpText = helpText;
		this.defaultYes = defaultYes;
		this.baseline = baseline;
		this.displayOrder = displayOrder;
		this.moduleCodes = moduleCodes;
	}

	public Set<AppModule> modules() {
		return Arrays.stream(moduleCodes.split(",")).map(String::trim).map(AppModule::fromCode).flatMap(java.util.Optional::stream)
				.collect(Collectors.toCollection(() -> EnumSet.noneOf(AppModule.class)));
	}
}
