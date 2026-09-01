package com.operion.examination.api;

import java.time.LocalDate;

import com.operion.examination.ExamSchedule;

public record ExamScheduleResponse(
		Long id, Long examId, Long schoolClassId, Long sectionId, Long subjectId, LocalDate examDate, Double maxMarks, Double passMarks) {

	static ExamScheduleResponse from(ExamSchedule schedule) {
		return new ExamScheduleResponse(schedule.getId(), schedule.getExam().getId(), schedule.getSchoolClass().getId(),
				schedule.getSection() == null ? null : schedule.getSection().getId(), schedule.getSubject().getId(), schedule.getExamDate(),
				schedule.getMaxMarks(), schedule.getPassMarks());
	}
}
