package com.operion.examination.api;

import java.time.LocalDate;

/** {@code sectionId} is nullable - omit it for a whole-class schedule (applies to every section). Per #139. */
public record CreateExamScheduleRequest(Long schoolClassId, Long sectionId, Long subjectId, LocalDate examDate, Double maxMarks, Double passMarks) {
}
