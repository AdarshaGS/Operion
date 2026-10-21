package com.operion.document.pdf;

import java.util.Arrays;

/**
 * The page sizes a DocumentTemplate can pick, with the CSS each one needs. THERMAL_80MM
 * is for counter receipt printers: narrow, small margins; a long receipt simply runs on
 * to a second strip.
 */
public enum PageFormat {
	A4("A4", "A4", "14mm", false),
	A5("A5", "A5", "10mm", false),
	LETTER("Letter", "letter", "14mm", false),
	THERMAL_80MM("THERMAL_80MM", "80mm 200mm", "4mm", true);

	private final String code;
	private final String cssSize;
	private final String margin;
	private final boolean narrow;

	PageFormat(String code, String cssSize, String margin, boolean narrow) {
		this.code = code;
		this.cssSize = cssSize;
		this.margin = margin;
		this.narrow = narrow;
	}

	public String code() {
		return code;
	}

	public String cssSize() {
		return cssSize;
	}

	public String margin() {
		return margin;
	}

	public boolean narrow() {
		return narrow;
	}

	/** Unknown or missing codes fall back to A4 rather than failing a print. */
	public static PageFormat fromCode(String code) {
		return Arrays.stream(values()).filter(f -> f.code.equalsIgnoreCase(String.valueOf(code))).findFirst().orElse(A4);
	}
}
