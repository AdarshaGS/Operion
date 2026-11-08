package com.operion.organisation;

/**
 * A write was attempted against an academic year that is CLOSED. Extends
 * {@link IllegalStateException} so ApiExceptionHandler's existing conflict mapping turns it into
 * a 409 with this message, and so callers that already catch IllegalStateException keep working.
 */
public class ClosedAcademicYearException extends IllegalStateException {

	public ClosedAcademicYearException(String academicYearName, String action) {
		super("Academic year " + academicYearName + " is closed; " + action + " is not allowed");
	}
}
