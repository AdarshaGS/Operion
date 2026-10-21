package com.operion.backup;

/** A backup could not be taken; the message says why in terms an operator can act on. */
public class BackupFailedException extends RuntimeException {

	public BackupFailedException(String message) {
		super(message);
	}

	public BackupFailedException(String message, Throwable cause) {
		super(message, cause);
	}
}
