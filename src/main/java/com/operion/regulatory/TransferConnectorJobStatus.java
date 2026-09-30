package com.operion.regulatory;

/** PREPARED -> SUBMITTED | FAILED | CANCELLED; a FAILED job can be re-prepared. SUBMITTED and
 * CANCELLED are terminal. */
public enum TransferConnectorJobStatus {
	PREPARED,
	SUBMITTED,
	FAILED,
	CANCELLED
}
