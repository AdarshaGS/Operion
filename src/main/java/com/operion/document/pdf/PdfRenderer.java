package com.operion.document.pdf;

/**
 * One seam for turning a filled-in XHTML document into PDF bytes, same "swap the class,
 * not the callers" pattern as {@link com.operion.storage.AssetStorageService}. The
 * default is in-process OpenHTMLtoPDF; a headless-browser backend can replace it if
 * documents ever need complex-script (Indic) text shaping, which OpenHTMLtoPDF lacks.
 */
public interface PdfRenderer {

	/** {@code xhtml} must be well-formed XHTML; images must already be inlined as data: URIs. */
	byte[] render(String xhtml);
}
