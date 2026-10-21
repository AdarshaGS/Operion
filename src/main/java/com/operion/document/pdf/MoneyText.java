package com.operion.document.pdf;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;

/**
 * Amounts as printed on documents: figures and words, driven by the school's configured
 * currency (OrganisationConfiguration.defaultCurrency). INR uses Indian digit grouping
 * (1,23,456.00) and lakh/crore words - java.text.DecimalFormat can't express that
 * uneven grouping, hence hand-rolled. Every other currency uses international grouping
 * and million/billion words with the ISO code as the unit.
 */
public final class MoneyText {

	private static final String[] ONES = { "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
			"Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen" };
	private static final String[] TENS = { "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety" };

	private MoneyText() {
	}

	public static String format(BigDecimal amount, String currencyCode) {
		BigDecimal value = scaled(amount);
		String sign = value.signum() < 0 ? "-" : "";
		String[] parts = value.abs().toPlainString().split("\\.");
		String whole = isIndian(currencyCode) ? groupIndian(parts[0]) : groupInternational(parts[0]);
		return sign + symbol(currencyCode) + " " + whole + "." + parts[1];
	}

	public static String inWords(BigDecimal amount, String currencyCode) {
		BigDecimal value = scaled(amount).abs();
		long whole = value.longValue();
		int minor = value.remainder(BigDecimal.ONE).movePointRight(2).intValue();
		boolean indian = isIndian(currencyCode);
		String wholeWords = whole == 0 ? "Zero" : (indian ? indianWords(whole) : internationalWords(whole));
		if (indian) {
			return "Rupees " + wholeWords + (minor > 0 ? " and " + belowHundred(minor) + " Paise" : "") + " Only";
		}
		return wholeWords + (minor > 0 ? " and " + String.format("%02d", minor) + "/100" : "") + " " + code(currencyCode) + " Only";
	}

	/** Plain cardinal words, e.g. 2012 -> "Two Thousand Twelve"; zero is "Zero". */
	public static String numberInWords(long n) {
		return n == 0 ? "Zero" : internationalWords(n);
	}

	private static BigDecimal scaled(BigDecimal amount) {
		return (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
	}

	private static boolean isIndian(String currencyCode) {
		return "INR".equals(code(currencyCode));
	}

	private static String code(String currencyCode) {
		return currencyCode == null || currencyCode.isBlank() ? "INR" : currencyCode.trim().toUpperCase(Locale.ROOT);
	}

	private static String symbol(String currencyCode) {
		String code = code(currencyCode);
		if (code.equals("INR")) {
			return "₹";
		}
		try {
			return Currency.getInstance(code).getSymbol(Locale.US);
		} catch (IllegalArgumentException e) {
			return code;
		}
	}

	private static String groupIndian(String digits) {
		if (digits.length() <= 3) {
			return digits;
		}
		String last3 = digits.substring(digits.length() - 3);
		String rest = digits.substring(0, digits.length() - 3);
		StringBuilder out = new StringBuilder();
		while (rest.length() > 2) {
			out.insert(0, "," + rest.substring(rest.length() - 2));
			rest = rest.substring(0, rest.length() - 2);
		}
		return rest + out + "," + last3;
	}

	private static String groupInternational(String digits) {
		StringBuilder out = new StringBuilder(digits);
		for (int i = digits.length() - 3; i > 0; i -= 3) {
			out.insert(i, ',');
		}
		return out.toString();
	}

	private static String indianWords(long n) {
		StringBuilder out = new StringBuilder();
		n = append(out, n, 10_000_000L, "Crore", true);
		n = append(out, n, 100_000L, "Lakh", false);
		n = append(out, n, 1_000L, "Thousand", false);
		appendBelowThousand(out, (int) n);
		return out.toString().trim();
	}

	private static String internationalWords(long n) {
		StringBuilder out = new StringBuilder();
		n = append(out, n, 1_000_000_000L, "Billion", true);
		n = append(out, n, 1_000_000L, "Million", false);
		n = append(out, n, 1_000L, "Thousand", false);
		appendBelowThousand(out, (int) n);
		return out.toString().trim();
	}

	/** Appends "<count> <unit>" for the units part of n and returns the remainder. A top unit (crore/billion) may exceed 999. */
	private static long append(StringBuilder out, long n, long unit, String name, boolean top) {
		long count = n / unit;
		if (count > 0) {
			out.append(top && count > 999 ? internationalWords(count) : belowThousand((int) count)).append(' ').append(name).append(' ');
		}
		return n % unit;
	}

	private static void appendBelowThousand(StringBuilder out, int n) {
		if (n > 0) {
			out.append(belowThousand(n));
		}
	}

	private static String belowThousand(int n) {
		int hundreds = n / 100;
		int rest = n % 100;
		String words = hundreds > 0 ? ONES[hundreds] + " Hundred" : "";
		if (rest > 0) {
			words = (words.isEmpty() ? "" : words + " ") + belowHundred(rest);
		}
		return words;
	}

	private static String belowHundred(int n) {
		if (n < 20) {
			return ONES[n];
		}
		return TENS[n / 10] + (n % 10 > 0 ? " " + ONES[n % 10] : "");
	}
}
