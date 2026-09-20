package com.operion.customfield;

/** ARCHIVED stops a field from being offered on the create/edit form for new edits, but
 * existing records keep whatever value they already stored under that key - same
 * "archive, don't hard-delete the definition" reasoning as GradeLevelStatus. */
public enum CustomFieldDefinitionStatus {
	ACTIVE,
	ARCHIVED
}
