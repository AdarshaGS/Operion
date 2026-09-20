package com.operion.customfield;

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
 * One organisation-defined field an entity type can carry, e.g. ("STUDENT", "blood_group",
 * "Blood group", TEXT) - the admin-facing definition half of #146's custom fields
 * mechanism. The values themselves are NOT stored here (this is not EAV): each opted-in
 * entity (starting with {@code com.operion.student.Student}) carries its own values
 * directly in a {@code custom_fields} JSON column keyed by {@link #fieldKey}, so reading a
 * record's fields is a single-table read, not a join/aggregate over a values table.
 * {@code entityType} is a plain string, not an FK or enum - same polymorphic-reference
 * shape as {@code com.operion.audit.AuditLog.entityType} - so a future vertical can define
 * fields for its own record types (e.g. "PATIENT") without a change to this core class;
 * only the entity actually reading these definitions (e.g. StudentController, filtering by
 * "STUDENT") needs to know its own type name.
 */
@Getter
@Entity
@Table(name = "custom_field_definitions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomFieldDefinition extends TenantScopedEntity {

	@Column(name = "entity_type", nullable = false)
	private String entityType;

	@Column(name = "field_key", nullable = false)
	private String fieldKey;

	@Column(nullable = false)
	private String label;

	@Enumerated(EnumType.STRING)
	@Column(name = "data_type", nullable = false, length = 20)
	private CustomFieldDataType dataType;

	/** Nullable - comma-separated choices, only meaningful when dataType is SELECT. */
	private String options;

	@Column(nullable = false)
	private boolean required;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CustomFieldDefinitionStatus status;

	public CustomFieldDefinition(String entityType, String fieldKey, String label, CustomFieldDataType dataType,
			String options, boolean required) {
		this.entityType = entityType;
		this.fieldKey = fieldKey;
		this.label = label;
		this.dataType = dataType;
		this.options = options;
		this.required = required;
		this.status = CustomFieldDefinitionStatus.ACTIVE;
	}

	/** entityType/fieldKey are the structural identity of "which stored value this
	 * definition describes" - changing them would silently orphan every record's already-
	 * stored value under the old key, same reasoning as SchoolClass.updateDisplayName. */
	public void update(String label, CustomFieldDataType dataType, String options, boolean required) {
		this.label = label;
		this.dataType = dataType;
		this.options = options;
		this.required = required;
	}

	public void changeStatus(CustomFieldDefinitionStatus status) {
		this.status = status;
	}
}
