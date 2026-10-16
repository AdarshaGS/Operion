package com.operion.organisation.rollover;

import java.math.BigDecimal;

/** {@code feeIncreasePercent}: applied to fee amounts as they are copied (0 = unchanged, negative = reduction). */
public record RolloverOptions(BigDecimal feeIncreasePercent) {

	public RolloverOptions {
		feeIncreasePercent = feeIncreasePercent == null ? BigDecimal.ZERO : feeIncreasePercent;
	}
}
