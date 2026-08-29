package com.operion.hr.api;

public record SubmitJobApplicationRequest(
		String organisationSlug, String applicantName, String email, String specialization, Integer yearsExperience) {
}
