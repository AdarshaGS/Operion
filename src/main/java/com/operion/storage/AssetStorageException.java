package com.operion.storage;

/** A rejected upload (bad type, too large) or a storage-layer failure - always a 400, never a 500. */
public class AssetStorageException extends RuntimeException {

	public AssetStorageException(String message) {
		super(message);
	}

	public AssetStorageException(String message, Throwable cause) {
		super(message, cause);
	}
}
