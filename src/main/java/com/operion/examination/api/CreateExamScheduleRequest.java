package com.operion.examination.api;

import java.time.LocalDate;

public record CreateExamScheduleRequest(Long schoolClassId, Long subjectId, LocalDate examDate, Double maxMarks, Double passMarks) {
}
