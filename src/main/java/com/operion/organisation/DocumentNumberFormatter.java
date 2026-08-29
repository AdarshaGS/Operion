package com.operion.organisation;

import java.util.regex.Pattern;

/**
 * Renders a configurable document-numbering template (admission/invoice/receipt numbers,
 * see OrganisationBranding) against an atomic sequence value. Deliberately generic - no
 * document-type-specific behavior lives here, only token substitution, so the same
 * formatter serves every numbered document type an org configures. Per #142.
 */
public final class DocumentNumberFormatter {

	/** {@code {SEQ}} or {@code {SEQ:width}} - the sequence number, zero-padded to width (default 1). */
	private static final Pattern SEQ_TOKEN = Pattern.compile("\\{SEQ(?::(\\d+))?}");

	private DocumentNumberFormatter() {
	}

	/**
	 * Supported tokens: {@code {SEQ}}/{@code {SEQ:n}} (zero-padded sequence), {@code {AY}}
	 * (academic year label, may be null if the document type isn't academic-year-scoped),
	 * {@code {YYYY}} (4-digit calendar year).
	 */
	public static String format(String template, long sequence, String academicYearLabel, int calendarYear) {
		String result = SEQ_TOKEN.matcher(template).replaceAll(match -> {
			int width = match.group(1) != null ? Integer.parseInt(match.group(1)) : 1;
			return String.format("%0" + width + "d", sequence);
		});
		result = result.replace("{YYYY}", String.valueOf(calendarYear));
		if (academicYearLabel != null) {
			result = result.replace("{AY}", academicYearLabel);
		}
		return result;
	}
}
