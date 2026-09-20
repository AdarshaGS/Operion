package com.operion.onboarding;

import com.operion.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * The global, platform-wide reference catalog behind a fresh organisation's editable
 * {@link SchoolTemplateItem} rows - seeded entirely by data in
 * {@code V100__school_template_master_items.sql}, the same convention as {@code Permission}
 * (a shared, non-tenant-scoped catalog seeded by migration, never by a Java class). The app
 * never writes to this table; {@link SchoolTemplateItemService#ensureSeeded()} only reads
 * it, once per organisation, to create that organisation's own independently-editable copy.
 */
@Getter
@Entity
@Table(name = "school_template_master_items")
public class SchoolTemplateMasterItem extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SchoolTemplateItemCategory category;

	private String name;
	private String code;

	@Column(length = 500)
	private String description;

	@Column(name = "sequence_order")
	private Integer sequenceOrder;

	private String stage;

	@Column(name = "category_type", length = 20)
	private String categoryType;

	@Column(name = "min_percentage")
	private Double minPercentage;

	private String remark;

	@Column(name = "permission_codes", length = 2000)
	private String permissionCodes;

	protected SchoolTemplateMasterItem() {
	}

	/** Public specifically so tests can build fixture rows (Flyway, and therefore
	 * V100's seed data, is disabled under the H2 test profile) - same reasoning as
	 * Permission's constructor. The app itself never constructs one; production rows
	 * only ever come from the V100 migration. */
	public SchoolTemplateMasterItem(SchoolTemplateItemCategory category, String name, String code, String description,
			Integer sequenceOrder, String stage, String categoryType, Double minPercentage, String remark, String permissionCodes) {
		this.category = category;
		this.name = name;
		this.code = code;
		this.description = description;
		this.sequenceOrder = sequenceOrder;
		this.stage = stage;
		this.categoryType = categoryType;
		this.minPercentage = minPercentage;
		this.remark = remark;
		this.permissionCodes = permissionCodes;
	}
}
