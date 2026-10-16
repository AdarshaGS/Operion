package com.operion.onboarding.api;

import java.util.List;

import com.operion.onboarding.SchoolTemplateCategoryStatus;
import com.operion.onboarding.SchoolTemplateStatus;

public record SchoolTemplatePreviewResponse(
		List<SchoolTemplateCategoryStatus> grades,
		List<SchoolTemplateCategoryStatus> departments,
		List<SchoolTemplateCategoryStatus> designations,
		List<SchoolTemplateCategoryStatus> roles,
		List<SchoolTemplateCategoryStatus> feeCategories,
		List<SchoolTemplateCategoryStatus> itemCategories,
		List<SchoolTemplateCategoryStatus> items,
		List<SchoolTemplateCategoryStatus> assetTypes,
		List<SchoolTemplateCategoryStatus> leaveTypes,
		List<SchoolTemplateCategoryStatus> classes,
		List<SchoolTemplateCategoryStatus> sections,
		List<SchoolTemplateCategoryStatus> subjects,
		boolean gradingScaleExists) {

	public static SchoolTemplatePreviewResponse from(SchoolTemplateStatus status) {
		return new SchoolTemplatePreviewResponse(status.grades(), status.departments(), status.designations(), status.roles(),
				status.feeCategories(), status.itemCategories(), status.items(),
				status.assetTypes(), status.leaveTypes(), status.classes(), status.sections(), status.subjects(), status.gradingScaleExists());
	}
}
