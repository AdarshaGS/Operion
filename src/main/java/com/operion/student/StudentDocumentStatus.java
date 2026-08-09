package com.operion.student;

/** Record lifecycle - deliberately separate from DocumentVerificationStatus, per the
 * attendance_status naming precedent in ai-context/erp-system-plan.md §3.1: don't let a
 * generic lifecycle status collide with a domain-specific one on the same entity. */
public enum StudentDocumentStatus {
	ACTIVE,
	SUPERSEDED
}
