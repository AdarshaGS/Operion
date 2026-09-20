package com.operion.finance;

/** How a fee structure's amount is meant to be paid - a reporting/display label only; the
 * actual due dates still come from FeeStructureInstallment, which stays fully admin-editable
 * regardless of this label. Per #281. */
public enum PaymentFrequency {
	MONTHLY,
	QUARTERLY,
	HALF_YEARLY,
	ONE_SHOT
}
