package com.operion.timetable.api;

/** Both null means the class follows the default school day. */
public record ClassPeriodTemplateResponse(Long classId, Long templateId, String templateName) {
}
