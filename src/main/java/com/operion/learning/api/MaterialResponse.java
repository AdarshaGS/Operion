package com.operion.learning.api;

import com.operion.learning.Labels;
import com.operion.learning.LearningMaterial;
import com.operion.storage.AssetStorageService;

public record MaterialResponse(Long id, Long classId, String className, Long subjectId, String subjectName, String title,
		String description, String kind, String fileUrl, String url, String uploadedByName, String status) {

	public static MaterialResponse from(LearningMaterial m, AssetStorageService storage) {
		return new MaterialResponse(m.getId(), m.getSchoolClass().getId(), Labels.className(m.getSchoolClass()),
				m.getSubject() == null ? null : m.getSubject().getId(), m.getSubject() == null ? null : m.getSubject().getName(),
				m.getTitle(), m.getDescription(), m.getKind().name(), Labels.url(storage, m.getAttachmentRef()), m.getUrl(),
				Labels.personName(m.getUploadedBy()), m.getStatus().name());
	}
}
