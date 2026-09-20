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

	@Setter
	private boolean gradingScaleCreated;

	@Setter
	private boolean gradingScaleAlreadyExists;
}
