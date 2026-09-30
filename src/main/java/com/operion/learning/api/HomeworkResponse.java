package com.operion.learning.api;

import java.time.LocalDate;

import com.operion.learning.Homework;
import com.operion.learning.HomeworkService;
import com.operion.learning.Labels;
import com.operion.storage.AssetStorageService;

public record HomeworkResponse(Long id, Long sectionId, String className, String sectionName, Long subjectId,
		String subjectName, String title, String description, LocalDate dueDate, String attachmentRef,
		String attachmentUrl, String status, String assignedByName, int enrolled, int submitted, int reviewed) {

	public static HomeworkResponse from(HomeworkService.HomeworkView view, AssetStorageService storage) {
		Homework h = view.homework();
		return new HomeworkResponse(h.getId(), h.getSection().getId(), Labels.className(h.getSection()),
				h.getSection().getName(), h.getSubject().getId(), h.getSubject().getName(), h.getTitle(),
				h.getDescription(), h.getDueDate(), h.getAttachmentRef(), Labels.url(storage, h.getAttachmentRef()),
				h.getStatus().name(), Labels.personName(h.getAssignedBy()), view.enrolled(), view.submitted(),
				view.reviewed());
	}
}
