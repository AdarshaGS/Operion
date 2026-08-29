package com.operion.hr.api;

import java.time.Instant;

import com.operion.hr.JobApplication;

public record JobApplicationResponse(Long id, String applicantName, String email, String specialization, Integer yearsExperience,
		String status, Instant appliedAt, Long decidedBy, Instant decidedAt) {

	public static JobApplicationResponse from(JobApplication jobApplication) {
		return new JobApplicationResponse(jobApplication.getId(), jobApplication.getApplicantName(), jobApplication.getEmail(),
				jobApplication.getSpecialization(), jobApplication.getYearsExperience(), jobApplication.getStatus().name(),
				jobApplication.getAppliedAt(), jobApplication.getDecidedBy(), jobApplication.getDecidedAt());
	}
}
