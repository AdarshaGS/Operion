package com.operion.certificate;

/** PENDING_APPROVAL only for types that need approval; others start APPROVED and wait to be issued. */
public enum CertificateRequestStatus {
	PENDING_APPROVAL,
	APPROVED,
	REJECTED,
	ISSUED,
	CANCELLED
}
