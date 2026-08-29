package com.operion.student.api;

import java.time.LocalDate;

import com.operion.student.Applicant;

public record ApplicantResponse(Long id, Long personId, LocalDate inquiryDate, String source, String notes, String status) {

	static ApplicantResponse from(Applicant applicant) {
		return new ApplicantResponse(applicant.getId(), applicant.getPerson().getId(), applicant.getInquiryDate(),
				applicant.getSource(), applicant.getNotes(), applicant.getStatus().name());
	}
}
