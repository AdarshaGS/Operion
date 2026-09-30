package com.operion.learning.api;

import java.time.Instant;
import java.time.LocalDate;

import com.operion.learning.Homework;
import com.operion.learning.HomeworkSubmission;
import com.operion.learning.Labels;
import com.operion.storage.AssetStorageService;

/** submissionStatus is NOT_SUBMITTED until the guardian submits, then SUBMITTED / REVIEWED. */
public record ChildHomeworkResponse(Long id, String subjectName, String title, String description, LocalDate dueDate,
		String attachmentUrl, String assignedByName, String submissionStatus, boolean late, Instant submittedAt,
		String submissionText, String submissionAttachmentUrl, String feedback) {

	public static ChildHomeworkResponse from(Homework h, HomeworkSubmission s, AssetStorageService storage) {
		return new ChildHomeworkResponse(h.getId(), h.getSubject().getName(), h.getTitle(), h.getDescription(),
				h.getDueDate(), Labels.url(storage, h.getAttachmentRef()), Labels.personName(h.getAssignedBy()),
				s == null ? "NOT_SUBMITTED" : s.getStatus().name(), s != null && s.isLate(),
				s == null ? null : s.getSubmittedAt(), s == null ? null : s.getSubmissionText(),
				s == null ? null : Labels.url(storage, s.getAttachmentRef()), s == null ? null : s.getFeedback());
	}
}
