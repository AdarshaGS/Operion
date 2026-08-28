package com.operion.reporting;

/** A report's query failed to execute (bad SQL, timeout, row limit) - mapped to 400 by ApiExceptionHandler. */
public class ReportExecutionException extends RuntimeException {

	public ReportExecutionException(String message, Throwable cause) {
		super(message, cause);
	}
}
