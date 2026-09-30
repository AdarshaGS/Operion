package com.operion.regulatory;

/** INACTIVE schemas can't be exported but keep their field mapping - same "retire, don't
 * delete" shape as CustomFieldDefinitionStatus. */
public enum RegulatoryExportStatus {
	ACTIVE,
	INACTIVE
}
