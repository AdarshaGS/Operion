package com.operion.platform.auth;

public class InvalidPlatformTokenException extends RuntimeException {

	public InvalidPlatformTokenException(String message, Throwable cause) {
		super(message, cause);
	}
}
