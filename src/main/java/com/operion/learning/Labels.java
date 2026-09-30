package com.operion.learning;

import com.operion.academic.SchoolClass;
import com.operion.academic.Section;
import com.operion.identity.Person;
import com.operion.storage.AssetStorageService;

/** Display helpers shared by the learning response DTOs. */
public final class Labels {

	private Labels() {
	}

	public static String className(Section section) {
		return className(section.getSchoolClass());
	}

	public static String className(SchoolClass schoolClass) {
		return schoolClass.getDisplayName() != null && !schoolClass.getDisplayName().isBlank()
				? schoolClass.getDisplayName()
				: schoolClass.getGradeLevel().getName();
	}

	public static String personName(Person person) {
		return person.getFirstName() + " " + person.getLastName();
	}

	/** Null-safe: a record without an attachment has no URL. */
	public static String url(AssetStorageService storage, String reference) {
		return reference == null ? null : storage.resolveUrl(reference);
	}
}
