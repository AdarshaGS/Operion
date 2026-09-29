package com.operion.common;

/** Thrown when a public endpoint's per-caller rate limit is exceeded; mapped to HTTP 429. */
public class TooManyRequestsException extends RuntimeException {

	public TooManyRequestsException(String message) {
		super(message);
	}
}
