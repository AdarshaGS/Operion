package com.operion.examination.api;

public record CorrectMarksRequest(Double marksObtained, boolean absent, String remarks) {
}
