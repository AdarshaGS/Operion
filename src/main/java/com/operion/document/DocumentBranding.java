package com.operion.document;

/**
 * The school identity printed on a document. Text only plus asset references - images are
 * loaded at render time ({@link DocumentBrandingService#imageDataUri}) so this record can
 * be frozen into an issued document's snapshot without embedding image bytes in it.
 */
public record DocumentBranding(String schoolName, String addressLine, String affiliationText, String footerText,
		String logoRef, String stampRef, String signatureRef, String currencyCode) {
}
