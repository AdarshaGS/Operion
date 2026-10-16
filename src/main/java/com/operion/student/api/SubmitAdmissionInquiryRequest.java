package com.operion.student.api;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

/** Public form body. {@code website} is a honeypot - real users never see or fill it. */
public record SubmitAdmissionInquiryRequest(@NotBlank @Size(max = 100) String firstName, @NotBlank @Size(max = 100) String lastName,
		@NotNull @Past LocalDate dateOfBirth, @NotBlank @Size(max = 30) String gender, Long desiredGradeId,
		@NotBlank @Size(max = 200) String guardianName, @NotBlank @Size(max = 30) String guardianPhone,
		@Email @Size(max = 200) String guardianEmail, @Size(max = 2000) String notes, String website) {
}
