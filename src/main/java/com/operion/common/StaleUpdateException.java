package com.operion.common;

/** Thrown when an edit was made against an out-of-date copy of a record (its version no longer matches); mapped to HTTP 409. */
public class StaleUpdateException extends RuntimeException {

	public StaleUpdateException(String message) {
		super(message);
	}
}
