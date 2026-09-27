package com.operion.library.api;

import java.time.LocalDate;

/** damaged defaults to false (unboxed at the call site) - see #168: returning a copy can
 * optionally record it as damaged instead of available again. */
public record ReturnBookRequest(LocalDate returnedDate, Boolean damaged) {

	public boolean isDamaged() {
		return Boolean.TRUE.equals(damaged);
	}
}
