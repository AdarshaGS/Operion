package com.operion.privacy;

/** RECEIVED -> IDENTITY_VERIFIED -> COMPLETED; REJECTED is reachable from either open state.
 * COMPLETED and REJECTED are terminal. */
public enum DataSubjectRequestStatus {
	RECEIVED,
	IDENTITY_VERIFIED,
	COMPLETED,
	REJECTED
}
