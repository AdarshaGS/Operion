package com.operion.academic.api;

import java.time.LocalDate;

public record AssignTeacherRequest(Long subjectId, Long teacherPersonId, String assignmentType, LocalDate startDate) {
}
