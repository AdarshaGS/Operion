package com.operion.inventory;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Org-scoped lookup (Stationery, Lab Equipment, Sports...), same pattern as FeeCategory. */
@Getter
@Entity
@Table(name = "item_categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemCategory extends TenantScopedEntity {

	@Column(nullable = false)
	private String code;

	@Column(nullable = false)
	private String name;

	/** Nullable. */
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ItemCategoryStatus status;

	public ItemCategory(String code, String name, String description) {
		this.code = code;
		this.name = name;
		this.description = description;
		this.status = ItemCategoryStatus.ACTIVE;
	}
}
