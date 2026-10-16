package com.operion.identity;

import java.util.Locale;
import java.util.Set;

import com.operion.common.InvalidRequestException;

/** The genders the app offers (see GENDER_OPTIONS in the web client); anything else is a 400 rather than free text stored as-is. */
public final class Genders {

	private static final Set<String> ALLOWED = Set.of("male", "female", "other");

	private Genders() {
	}

	/** Returns the value unchanged when it is an allowed gender (case-insensitive); null/blank pass through, callers decide whether the field is required. */
	public static String validate(String gender) {
		if (gender == null || gender.isBlank()) {
			return gender;
		}
		if (!ALLOWED.contains(gender.trim().toLowerCase(Locale.ROOT))) {
			throw new InvalidRequestException("gender must be one of Male, Female, Other");
		}
		return gender;
	}
}
