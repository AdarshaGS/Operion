package com.operion.student;

import java.util.List;

/**
 * Thrown by {@link AdmissionFeeGate} when the organisation blocks academic writes for students whose admission
 * fee is unpaid and at least one enrollment in the call is PENDING. Carries every offending enrollment id so
 * the client can say which students to take out of the batch. Mapped to 409 ADMISSION_FEE_PENDING.
 */
public class AdmissionFeePendingException extends RuntimeException {

	private final List<Long> enrollmentIds;

	public AdmissionFeePendingException(String action, List<Long> enrollmentIds) {
		super("Cannot " + action + ": the admission fee is pending for " + (enrollmentIds.size() == 1
				? "enrollment " + enrollmentIds.get(0)
				: enrollmentIds.size() + " enrollments " + enrollmentIds));
		this.enrollmentIds = List.copyOf(enrollmentIds);
	}

	public List<Long> getEnrollmentIds() {
		return enrollmentIds;
	}
}
