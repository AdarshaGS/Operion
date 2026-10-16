package com.operion.common.imports;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns an exception thrown while importing one row into a message safe to show the
 * uploader. Business-rule rejections (IllegalArgumentException / IllegalStateException
 * with a message we wrote) pass through; anything that would leak internals - a JDBC/SQL
 * message, a raw NumberFormatException, an enum class name - is replaced by a generic
 * line and the original is logged.
 */
public final class ImportRowErrors {

	private static final Logger log = LoggerFactory.getLogger(ImportRowErrors.class);

	private ImportRowErrors() {
	}

	public static String message(Exception e) {
		String message = e.getMessage();
		if (e instanceof NumberFormatException) {
			return "A numeric column contains a value that is not a valid number";
		}
		if (e instanceof java.time.format.DateTimeParseException) {
			return "A date column contains a value that is not a valid date - use the format YYYY-MM-DD";
		}
		if (e instanceof IllegalArgumentException || e instanceof IllegalStateException) {
			if (message == null || message.isBlank()) {
				return "Row is invalid";
			}
			if (message.startsWith("No enum constant") || message.startsWith("For input string")) {
				return "A column contains a value that is not one of the allowed options";
			}
			return message;
		}
		log.warn("Import row failed unexpectedly", e);
		return "Row could not be saved - it conflicts with existing data or breaks a rule";
	}
}
