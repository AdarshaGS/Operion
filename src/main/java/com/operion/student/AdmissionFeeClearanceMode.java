package com.operion.student;

public enum AdmissionFeeClearanceMode {
	/** Today's behaviour: enrolment does not touch fees. */
	OFF,
	/** Run the admission-fee workflow and show the status; block nothing. */
	TRACK,
	/** Run the workflow; a later phase also rejects academic writes for PENDING students. */
	BLOCK
}
