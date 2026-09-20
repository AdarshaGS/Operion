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
		boolean gradingScaleExists) {

	public static SchoolTemplatePreviewResponse from(SchoolTemplateStatus status) {
		return new SchoolTemplatePreviewResponse(status.grades(), status.departments(), status.designations(), status.roles(),
				status.feeCategories(), status.itemCategories(), status.gradingScaleExists());
	}
}
