package com.operion.common;

import org.springframework.http.ContentDisposition;

/**
 * Builds a Content-Disposition value from a name that may contain user-supplied text. A raw name in
 * {@code filename="..."} lets a quote end the parameter and CR/LF split the header (response splitting / header
 * injection), and a path separator makes browsers save elsewhere - so the name is reduced to a safe character set and
 * Spring's builder does the quoting.
 */
public final class DownloadFilenames {

	private static final int MAX_BASE_LENGTH = 100;

	private DownloadFilenames() {
	}

	/** {@code attachment; filename="<safe base>.<extension>"}; extension is a trusted constant such as "csv". */
	public static String attachment(String baseName, String extension) {
		return ContentDisposition.attachment().filename(safeBaseName(baseName) + "." + extension).build().toString();
	}

	static String safeBaseName(String baseName) {
		String safe = baseName == null ? "" : baseName.replaceAll("[^A-Za-z0-9._-]", "_");
		safe = safe.replaceAll("^\\.+", "");
		if (safe.length() > MAX_BASE_LENGTH) {
			safe = safe.substring(0, MAX_BASE_LENGTH);
		}
		return safe.isEmpty() ? "download" : safe;
	}
}
