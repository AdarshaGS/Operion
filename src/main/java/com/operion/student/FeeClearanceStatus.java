package com.operion.student;

/**
 * Where a student's enrollment stands against the admission fee. Stored on the enrollment and only ever
 * changed by finance code in the same transaction as the money movement, so it never has to be derived
 * from invoices on a read path.
 */
public enum FeeClearanceStatus {
	/** No admission fee applies: existing students, promotions, CSV imports, the setting being OFF, FEES disabled, or no fee required for the class. */
	NOT_REQUIRED,
	/** Admission invoices exist and are not yet settled in full. */
	PENDING,
	/** Every admission invoice is settled. Never reverted by a later bounce or refund. */
	CLEARED,
	/** Cleared by a manual override. Never changed by payment activity. */
	EXEMPTED
}
