package com.operion.performance;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** An inclusive date range. */
public record KpiPeriod(LocalDate from, LocalDate to) {

	public int days() {
		return (int) ChronoUnit.DAYS.between(from, to) + 1;
	}

	/** The range of the same length that ends the day before this one starts. */
	public KpiPeriod previous() {
		return new KpiPeriod(from.minusDays(days()), from.minusDays(1));
	}

	public Instant startInstant() {
		return from.atStartOfDay(ZoneId.systemDefault()).toInstant();
	}

	/** Exclusive upper bound. */
	public Instant endInstant() {
		return to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
	}
}
