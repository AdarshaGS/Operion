package com.operion.onboarding.api;

import java.util.Set;

/** Shared create/update request shape - every field is meaningful only for some
 * categories (see SchoolTemplateItem's class doc); the caller sends whichever ones apply
 * to the category being created or edited and leaves the rest null. */
public record SaveSchoolTemplateItemRequest(String name, String code, String description, Integer sequenceOrder, String stage,
		String categoryType, Double minPercentage, String remark, Set<String> permissionCodes) {
}
