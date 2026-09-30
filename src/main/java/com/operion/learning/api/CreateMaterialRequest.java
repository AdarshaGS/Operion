package com.operion.learning.api;

import com.operion.learning.MaterialKind;

public record CreateMaterialRequest(Long classId, Long subjectId, String title, String description, MaterialKind kind,
		String attachmentRef, String url) {
}
