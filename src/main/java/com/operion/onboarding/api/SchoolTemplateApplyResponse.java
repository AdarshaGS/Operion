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
		List<String> itemsCreated, List<String> itemsSkipped,
		List<String> assetTypesCreated, List<String> assetTypesSkipped,
		List<String> leaveTypesCreated, List<String> leaveTypesSkipped,
		List<String> classesCreated, List<String> classesSkipped, String classesNote,
		List<String> sectionsCreated, List<String> sectionsSkipped,
		List<String> subjectsCreated, List<String> subjectsSkipped,
		List<String> certificateTypesCreated, List<String> certificateTypesSkipped,
		boolean gradingScaleCreated, boolean gradingScaleAlreadyExists) {

	public static SchoolTemplateApplyResponse from(SchoolTemplateResult result) {
		return new SchoolTemplateApplyResponse(
				result.getGradesCreated(), result.getGradesSkipped(),
				result.getDepartmentsCreated(), result.getDepartmentsSkipped(),
				result.getDesignationsCreated(), result.getDesignationsSkipped(),
				result.getRolesCreated(), result.getRolesSkipped(),
				result.getFeeCategoriesCreated(), result.getFeeCategoriesSkipped(),
				result.getItemCategoriesCreated(), result.getItemCategoriesSkipped(),
				result.getItemsCreated(), result.getItemsSkipped(),
				result.getAssetTypesCreated(), result.getAssetTypesSkipped(),
				result.getLeaveTypesCreated(), result.getLeaveTypesSkipped(),
				result.getClassesCreated(), result.getClassesSkipped(), result.getClassesNote(),
				result.getSectionsCreated(), result.getSectionsSkipped(),
				result.getSubjectsCreated(), result.getSubjectsSkipped(),
				result.getCertificateTypesCreated(), result.getCertificateTypesSkipped(),
				result.isGradingScaleCreated(), result.isGradingScaleAlreadyExists());
	}
}
