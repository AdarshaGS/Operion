package com.operion.payroll;

public enum CalculationBasis {
	/** The structure line's value is a fixed amount per pay period. */
	FLAT,
	/** The structure line's value is a percentage (0-100) of the structure's basic amount. */
	PERCENT_OF_BASIC
}
