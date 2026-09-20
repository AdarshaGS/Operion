package com.operion.onboarding.api;

import java.util.List;

import com.operion.onboarding.SchoolTemplateResult;

public record SchoolTemplateApplyResponse(
		List<String> gradesCreated, List<String> gradesSkipped,
		List<String> departmentsCreated, List<String> departmentsSkipped,
		List<String> designationsCreated, List<String> designationsSkipped,
		List<String> rolesCreated, List<String> rolesSkipped,
		List<String> feeCategoriesCreated, List<String> feeCategoriesSkipped,
		List<String> itemCategoriesCreated, List<String> itemCategoriesSkipped,
		boolean gradingScaleCreated, boolean gradingScaleAlreadyExists) {

	public static SchoolTemplateApplyResponse from(SchoolTemplateResult result) {
		return new SchoolTemplateApplyResponse(
				result.getGradesCreated(), result.getGradesSkipped(),
				result.getDepartmentsCreated(), result.getDepartmentsSkipped(),
				result.getDesignationsCreated(), result.getDesignationsSkipped(),
				result.getRolesCreated(), result.getRolesSkipped(),
				result.getFeeCategoriesCreated(), result.getFeeCategoriesSkipped(),
				result.getItemCategoriesCreated(), result.getItemCategoriesSkipped(),
				result.isGradingScaleCreated(), result.isGradingScaleAlreadyExists());
	}
}
