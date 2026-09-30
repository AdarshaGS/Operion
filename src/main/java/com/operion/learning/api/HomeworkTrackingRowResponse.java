package com.operion.learning.api;

import java.time.Instant;

import com.operion.learning.HomeworkService;
import com.operion.learning.HomeworkSubmission;
import com.operion.learning.Labels;
import com.operion.storage.AssetStorageService;

/** submissionId is null when the student hasn't submitted; status is then NOT_SUBMITTED. */
public record HomeworkTrackingRowResponse(Long studentId, String studentName, Integer rollNumber, Long submissionId,
		String status, boolean late, Instant submittedAt, String submissionText, String attachmentUrl, String feedback) {

	public record Reviewed(Long submissionId, String status, String feedback) {
	}

	public static HomeworkTrackingRowResponse from(HomeworkService.TrackingRow row, AssetStorageService storage) {
		var student = row.enrollment().getStudent();
		HomeworkSubmission s = row.submission();
		if (s == null) {
			return new HomeworkTrackingRowResponse(student.getId(), Labels.personName(student.getPerson()),
					row.enrollment().getRollNumber(), null, "NOT_SUBMITTED", false, null, null, null, null);
		}
		return new HomeworkTrackingRowResponse(student.getId(), Labels.personName(student.getPerson()),
				row.enrollment().getRollNumber(), s.getId(), s.getStatus().name(), s.isLate(), s.getSubmittedAt(),
				s.getSubmissionText(), Labels.url(storage, s.getAttachmentRef()), s.getFeedback());
	}
}
