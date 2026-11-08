package com.operion.organisation;

import java.util.regex.Pattern;

import com.operion.common.InvalidRequestException;

/**
 * Renders a configurable document-numbering template (admission/invoice/receipt numbers,
 * see OrganisationBranding) against an atomic sequence value. Deliberately generic - no
 * document-type-specific behavior lives here, only token substitution, so the same
 * formatter serves every numbered document type an org configures. Per #142.
 */
public final class DocumentNumberFormatter {

	/** {@code {SEQ}} or {@code {SEQ:width}} - the sequence number, zero-padded to width (default 1). */
	private static final Pattern SEQ_TOKEN = Pattern.compile("\\{SEQ(?::(\\d+))?}");

	/** Widest zero-padding a {@code {SEQ:n}} may ask for. An unbounded n makes {@code String.format} allocate n characters
	 * per number issued (an admin-set template of {SEQ:2000000000} is a one-request OOM) - and a long is at most 19 digits. */
	public static final int MAX_SEQ_WIDTH = 12;

	private DocumentNumberFormatter() {
	}

	/** Rejects a template whose {@code {SEQ:n}} width is above {@link #MAX_SEQ_WIDTH} (or 0), for callers that save templates. */
	public static void requireValid(String template) {
		if (template == null) {
			return;
		}
		var matcher = SEQ_TOKEN.matcher(template);
		while (matcher.find()) {
			if (matcher.group(1) != null && (matcher.group(1).length() > 2 || Integer.parseInt(matcher.group(1)) > MAX_SEQ_WIDTH
					|| Integer.parseInt(matcher.group(1)) < 1)) {
				throw new InvalidRequestException("The sequence width in " + matcher.group() + " must be between 1 and " + MAX_SEQ_WIDTH);
			}
		}
	}

	/**
	 * Supported tokens: {@code {SEQ}}/{@code {SEQ:n}} (zero-padded sequence), {@code {AY}}
	 * (academic year label, may be null if the document type isn't academic-year-scoped),
	 * {@code {YYYY}} (4-digit calendar year).
	 */
	public static String format(String template, long sequence, String academicYearLabel, int calendarYear) {
		String result = SEQ_TOKEN.matcher(template).replaceAll(match -> {
			// Already-saved templates are not re-validated, so clamp here too rather than trust that requireValid ran.
			int width = match.group(1) == null ? 1 : widthOf(match.group(1));
			return String.format("%0" + width + "d", sequence);
		});
		result = result.replace("{YYYY}", String.valueOf(calendarYear));
		if (academicYearLabel != null) {
			result = result.replace("{AY}", academicYearLabel);
		}
		return result;
	}

	/** The digits as a width in [1, MAX_SEQ_WIDTH]; a many-digit value would overflow parseInt, so it is capped by length first. */
	private static int widthOf(String digits) {
		if (digits.length() > 2) {
			return MAX_SEQ_WIDTH;
		}
		return Math.max(1, Math.min(MAX_SEQ_WIDTH, Integer.parseInt(digits)));
	}
}
