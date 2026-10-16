package com.operion.finance.api;

import com.operion.common.InvalidRequestException;

/** Turns a missing required request field into a 400 instead of a null reaching a repository lookup (a 500). */
final class RequestChecks {

	private RequestChecks() {
	}

	static <T> T required(T value, String field) {
		if (value == null) {
			throw new InvalidRequestException(field + " is required");
		}
		return value;
	}
}
