package com.operion.document.pdf;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** A rendered PDF as a download. Never cached - these hold personal and payment data. */
public final class PdfResponses {

	private PdfResponses() {
	}

	public static ResponseEntity<byte[]> attachment(byte[] pdf, String baseName) {
		String safe = baseName == null ? "document" : baseName.replaceAll("[^A-Za-z0-9._-]", "_");
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.cacheControl(CacheControl.noStore())
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(safe + ".pdf").build().toString())
				.body(pdf);
	}
}
