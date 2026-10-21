package com.operion.onboarding;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Mutable accumulator built up over the course of {@link SchoolTemplateService#apply()} -
 * one created/skipped name list per category, so the caller can show exactly what happened. */
@Getter
public class SchoolTemplateResult {

	private final List<String> gradesCreated = new ArrayList<>();
	private final List<String> gradesSkipped = new ArrayList<>();
	private final List<String> departmentsCreated = new ArrayList<>();
	private final List<String> departmentsSkipped = new ArrayList<>();
	private final List<String> designationsCreated = new ArrayList<>();
	private final List<String> designationsSkipped = new ArrayList<>();
	private final List<String> rolesCreated = new ArrayList<>();
	private final List<String> rolesSkipped = new ArrayList<>();
	private final List<String> feeCategoriesCreated = new ArrayList<>();
	private final List<String> feeCategoriesSkipped = new ArrayList<>();
	private final List<String> itemCategoriesCreated = new ArrayList<>();
	private final List<String> itemCategoriesSkipped = new ArrayList<>();

	private final List<String> itemsCreated = new ArrayList<>();
	private final List<String> itemsSkipped = new ArrayList<>();
	private final List<String> assetTypesCreated = new ArrayList<>();
	private final List<String> assetTypesSkipped = new ArrayList<>();

	private final List<String> leaveTypesCreated = new ArrayList<>();
	private final List<String> leaveTypesSkipped = new ArrayList<>();

	private final List<String> classesCreated = new ArrayList<>();
	private final List<String> classesSkipped = new ArrayList<>();

	private final List<String> sectionsCreated = new ArrayList<>();
	private final List<String> sectionsSkipped = new ArrayList<>();

	private final List<String> subjectsCreated = new ArrayList<>();
	private final List<String> subjectsSkipped = new ArrayList<>();

	private final List<String> certificateTypesCreated = new ArrayList<>();
	private final List<String> certificateTypesSkipped = new ArrayList<>();

	/** Why no classes could be created at all (no current academic year, no active campus); null when there was nothing in the way. */
	@Setter
	private String classesNote;

	@Setter
	private boolean gradingScaleCreated;

	@Setter
	private boolean gradingScaleAlreadyExists;
}
