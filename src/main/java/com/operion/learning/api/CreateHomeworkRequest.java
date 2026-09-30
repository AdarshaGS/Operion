package com.operion.learning.api;

import java.time.LocalDate;

public record CreateHomeworkRequest(Long sectionId, Long subjectId, String title, String description, LocalDate dueDate,
		String attachmentRef) {
}
