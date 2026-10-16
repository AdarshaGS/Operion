package com.operion.examination.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

/** {@code sectionId} is nullable - omit it for a whole-class schedule (applies to every section). Per #139. */
public record CreateExamScheduleRequest(@NotNull Long schoolClassId, Long sectionId, @NotNull Long subjectId, @NotNull LocalDate examDate,
		@NotNull @Positive Double maxMarks, @NotNull @PositiveOrZero Double passMarks) {
}
