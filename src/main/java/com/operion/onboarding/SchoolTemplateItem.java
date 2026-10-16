package com.operion.onboarding;

import java.util.Set;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One draft row in an organisation's editable "School setup template" catalog - what
 * {@link SchoolTemplateService#apply()} turns into a real Department/Designation/Role/
 * GradeLevel/FeeCategory/ItemCategory/GradingScaleBand row. Deliberately one flexible shape
 * across all nine categories rather than seven near-identical tables, since these rows
 * carry no business rules of their own - the real entities they produce already own those.
 * Most fields are only meaningful for a subset of categories (see the per-category static
 * factories below); unused fields for a given category are left null.
 */
@Getter
@Entity
@Table(name = "school_template_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolTemplateItem extends TenantScopedEntity {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SchoolTemplateItemCategory category;

	private String name;

	/** Fee/item category code. Nullable - only FEE_CATEGORY and ITEM_CATEGORY use it. */
	private String code;

	/** Nullable - fee/item category description, or a role's description. */
	@Column(length = 500)
	private String description;

	/** Nullable - relative ordering for GRADE and GRADING_BAND (resolved to an actual
	 * GradeLevel.sequenceOrder at apply-time, never trusted as a literal DB value, so it
	 * can never collide with an org's own manually-created grades). */
	@Column(name = "sequence_order")
	private Integer sequenceOrder;

	/** Nullable - GRADE's optional stage grouping (e.g. "Primary"). */
	private String stage;

	/** Nullable - FEE_CATEGORY's GENERAL/TRANSPORT discriminator, stored as plain text so
	 * this table doesn't couple to com.operion.finance.FeeCategoryType. */
	@Column(name = "category_type", length = 20)
	private String categoryType;

	/** Nullable - GRADING_BAND's minimum percentage threshold. */
	@Column(name = "min_percentage")
	private Double minPercentage;

	/** Nullable - GRADING_BAND's remark (e.g. "Outstanding"). */
	private String remark;

	/** Nullable - ROLE's permission codes, comma-separated (no separate join table for a
	 * draft row with no FK integrity to protect). */
	@Column(name = "permission_codes", length = 2000)
	private String permissionCodes;

	/** Nullable - the AppModule code this item belongs to; apply skips it while that module is
	 * switched off for the organisation. Null means the item is always applied. */
	@Column(name = "module_code", length = 40)
	private String moduleCode;

	/** Nullable - ITEM's and ASSET_TYPE's unit of measure ("PCS"/"BOX"/"LTR"), free text like Item.unit. */
	@Column(length = 20)
	private String unit;

	/** Nullable - ITEM's and ASSET_TYPE's parent inventory category, by ITEM_CATEGORY code (not id, so the
	 * template row survives that category being re-created). Apply skips the row if the org has no such category. */
	@Column(name = "item_category_code", length = 50)
	private String itemCategoryCode;

	/** False means the admin has marked this row "not required": it stays in the catalog, but preview and apply
	 * ignore it. Distinct from deleting it, so a school can switch rows off and back on. */
	@Column(nullable = false)
	private boolean included = true;

	private SchoolTemplateItem(SchoolTemplateItemCategory category, String name, String code, String description,
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

	public static SchoolTemplateItem grade(String name, int sequenceOrder, String stage) {
		return new SchoolTemplateItem(SchoolTemplateItemCategory.GRADE, name, null, null, sequenceOrder, stage, null, null, null, null);
	}

	public static SchoolTemplateItem department(String name) {
		return new SchoolTemplateItem(SchoolTemplateItemCategory.DEPARTMENT, name, null, null, null, null, null, null, null, null);
	}

	public static SchoolTemplateItem designation(String name) {
		return new SchoolTemplateItem(SchoolTemplateItemCategory.DESIGNATION, name, null, null, null, null, null, null, null, null);
	}

	public static SchoolTemplateItem role(String name, String description, String permissionCodes) {
		return new SchoolTemplateItem(SchoolTemplateItemCategory.ROLE, name, null, description, null, null, null, null, null, permissionCodes);
	}

	public static SchoolTemplateItem feeCategory(String code, String name, String description, String categoryType) {
		return new SchoolTemplateItem(SchoolTemplateItemCategory.FEE_CATEGORY, name, code, description, null, null, categoryType, null, null, null);
	}

	public static SchoolTemplateItem itemCategory(String code, String name, String description) {
		return new SchoolTemplateItem(SchoolTemplateItemCategory.ITEM_CATEGORY, name, code, description, null, null, null, null, null, null);
	}

	public static SchoolTemplateItem gradingBand(String grade, int sequenceOrder, double minPercentage, String remark) {
		return new SchoolTemplateItem(
				SchoolTemplateItemCategory.GRADING_BAND, grade, null, null, sequenceOrder, null, null, minPercentage, remark, null);
	}

	/** General-purpose factory for the management CRUD screen, where every field is
	 * caller-supplied rather than shaped by one of the category-specific factories above. */
	public static SchoolTemplateItem of(SchoolTemplateItemCategory category, String name, String code, String description,
			Integer sequenceOrder, String stage, String categoryType, Double minPercentage, String remark, String permissionCodes) {
		return new SchoolTemplateItem(category, name, code, description, sequenceOrder, stage, categoryType, minPercentage, remark, permissionCodes);
	}

	/** Copies the module tag from the catalog row an organisation's copy is made from. */
	public SchoolTemplateItem taggedWith(String moduleCode) {
		this.moduleCode = moduleCode;
		return this;
	}

	/** Sets the item-shaped fields (ITEM / ASSET_TYPE rows only); chained like {@link #taggedWith}. */
	public SchoolTemplateItem withItemDetails(String unit, String itemCategoryCode) {
		this.unit = unit;
		this.itemCategoryCode = itemCategoryCode;
		return this;
	}

	public void setIncluded(boolean included) {
		this.included = included;
	}

	public Set<String> permissionCodeSet() {
		return permissionCodes == null || permissionCodes.isBlank() ? Set.of() : Set.of(permissionCodes.split(","));
	}

	/** Generic edit - a management screen lets an admin change any field regardless of
	 * category, so this mirrors the create shape rather than exposing one setter per field. */
	public void update(String name, String code, String description, Integer sequenceOrder, String stage, String categoryType,
			Double minPercentage, String remark, String permissionCodes,
			String unit, String itemCategoryCode) {
		this.name = name;
		this.code = code;
		this.description = description;
		this.sequenceOrder = sequenceOrder;
		this.stage = stage;
		this.categoryType = categoryType;
		this.minPercentage = minPercentage;
		this.remark = remark;
		this.permissionCodes = permissionCodes;
		this.unit = unit;
		this.itemCategoryCode = itemCategoryCode;
	}
}
