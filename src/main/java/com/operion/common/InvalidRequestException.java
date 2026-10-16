package com.operion.common;

/** A request whose content breaks a business rule the caller can correct (e.g. an end date before its start date) - mapped to 400. */
public class InvalidRequestException extends RuntimeException {

	public InvalidRequestException(String message) {
		super(message);
	}
}
