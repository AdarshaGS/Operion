package com.operion.regulatory;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One output column: {@link #sourceField} is a key from {@link RegulatoryExportSources}
 * (or {@code custom:<key>} for a student custom field). References its schema by FK only,
 * like PurchaseOrderLine. */
@Getter
@Entity
@Table(name = "regulatory_export_fields")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegulatoryExportField extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "schema_id")
	private RegulatoryExportSchema schema;

	@Column(nullable = false)
	private int ordinal;

	@Column(name = "column_header", nullable = false)
	private String columnHeader;

	@Column(name = "source_field", nullable = false)
	private String sourceField;

	/** Nullable - written when the source value is blank. */
	@Column(name = "default_value")
	private String defaultValue;

	public RegulatoryExportField(RegulatoryExportSchema schema, int ordinal, String columnHeader, String sourceField,
			String defaultValue) {
		this.schema = schema;
		this.ordinal = ordinal;
		this.columnHeader = columnHeader;
		this.sourceField = sourceField;
		this.defaultValue = defaultValue;
	}
}
