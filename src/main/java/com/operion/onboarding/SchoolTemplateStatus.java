package com.operion.onboarding;

import java.util.List;

/** Result of {@link SchoolTemplateService#preview()} - one status list per category, plus a
 * single "does a default grading scale already exist" flag (grading scales aren't seeded by
 * name, only ever the first one). */
public record SchoolTemplateStatus(
		List<SchoolTemplateCategoryStatus> grades,
		List<SchoolTemplateCategoryStatus> departments,
		List<SchoolTemplateCategoryStatus> designations,
		List<SchoolTemplateCategoryStatus> roles,
		List<SchoolTemplateCategoryStatus> feeCategories,
		List<SchoolTemplateCategoryStatus> itemCategories,
		boolean gradingScaleExists) {
}
