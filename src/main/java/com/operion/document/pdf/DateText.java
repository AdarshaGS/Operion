package com.operion.document.pdf;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/** Dates as printed on official documents: "5 March 2012", and in words "Fifth March Two Thousand Twelve". */
public final class DateText {

	private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

	private DateText() {
	}

	public static String format(LocalDate date) {
		return date == null ? null : LONG.format(date);
	}

	public static String inWords(LocalDate date) {
		if (date == null) {
			return null;
		}
		return ordinal(date.getDayOfMonth()) + " " + date.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " "
				+ MoneyText.numberInWords(date.getYear());
	}

	static String ordinal(int n) {
		String cardinal = MoneyText.numberInWords(n);
		int space = cardinal.lastIndexOf(' ');
		String head = space < 0 ? "" : cardinal.substring(0, space + 1);
		String last = space < 0 ? cardinal : cardinal.substring(space + 1);
		String ordinalLast = switch (last) {
			case "One" -> "First";
			case "Two" -> "Second";
			case "Three" -> "Third";
			case "Five" -> "Fifth";
			case "Eight" -> "Eighth";
			case "Nine" -> "Ninth";
			case "Twelve" -> "Twelfth";
			default -> last.endsWith("y") ? last.substring(0, last.length() - 1) + "ieth" : last + "th";
		};
		return head + ordinalLast;
	}
}
