package com.operion.regulatory;

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
 * An organisation-defined mapping from Operion's own fields to an external reporting
 * layout (a government return, a funder's template, ...). Nothing regional is baked in or
 * seeded - an org that needs one builds it from the source-field catalog. {@link #entityType}
 * and {@link #code} are the structural identity and stay fixed after creation.
 */
@Getter
@Entity
@Table(name = "regulatory_export_schemas")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegulatoryExportSchema extends TenantScopedEntity {

	@Column(nullable = false)
	private String code;

	@Column(nullable = false)
	private String name;

	/** Nullable. */
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "entity_type", nullable = false, length = 20)
	private RegulatoryEntityType entityType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RegulatoryExportStatus status;

	public RegulatoryExportSchema(String code, String name, String description, RegulatoryEntityType entityType) {
		this.code = code;
		this.name = name;
		this.description = description;
		this.entityType = entityType;
		this.status = RegulatoryExportStatus.ACTIVE;
	}

	public void update(String name, String description) {
		this.name = name;
		this.description = description;
	}

	public void changeStatus(RegulatoryExportStatus status) {
		this.status = status;
	}
}
