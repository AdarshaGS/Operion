package com.operion.timetable.api;

public record SetTimetableEntryRequest(Long subjectId, Long teacherPersonId, String dayOfWeek, Long periodId) {
}
