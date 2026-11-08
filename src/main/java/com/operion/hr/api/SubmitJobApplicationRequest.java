package com.operion.hr.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Public form body - every field is capped so an anonymous caller cannot push oversized text into the table. */
public record SubmitJobApplicationRequest(
		@NotBlank @Size(max = 100) String organisationSlug, @NotBlank @Size(max = 200) String applicantName,
		@NotBlank @Email @Size(max = 200) String email, @Size(max = 255) String specialization,
		@PositiveOrZero @Max(70) Integer yearsExperience) {
}
