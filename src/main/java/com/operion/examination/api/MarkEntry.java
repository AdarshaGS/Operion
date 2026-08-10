package com.operion.examination.api;

public record MarkEntry(Long studentEnrollmentId, Double marksObtained, boolean absent, String remarks) {
}
