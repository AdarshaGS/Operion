package com.operion.common.imports;

/** The uploaded import file itself is unusable (missing, empty, wrong type, corrupt) - a
 * client error mapped to 400, unlike IllegalArgumentException which this API maps to 404. */
public class ImportFileException extends RuntimeException {

	public ImportFileException(String message) {
		super(message);
	}

	public ImportFileException(String message, Throwable cause) {
		super(message, cause);
	}
}
