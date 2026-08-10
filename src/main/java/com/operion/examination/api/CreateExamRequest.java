package com.operion.examination.api;

public record CreateExamRequest(Long academicYearId, String name, String examType) {
}
