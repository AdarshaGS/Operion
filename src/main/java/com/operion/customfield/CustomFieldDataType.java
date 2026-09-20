package com.operion.customfield;

/** How a custom field's value is entered/rendered. Values are still stored as plain JSON
 * scalars in the owning record's {@code custom_fields} column regardless of type -
 * dataType only drives which input the frontend renders and the SELECT-only meaning of
 * {@link CustomFieldDefinition#getOptions()}. */
public enum CustomFieldDataType {
	TEXT,
	NUMBER,
	DATE,
	BOOLEAN,
	SELECT
}
