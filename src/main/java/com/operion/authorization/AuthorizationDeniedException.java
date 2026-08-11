package com.operion.authorization;

public class AuthorizationDeniedException extends RuntimeException {

	public AuthorizationDeniedException(String message) {
		super(message);
	}
}
