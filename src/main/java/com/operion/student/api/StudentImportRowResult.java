package com.operion.student.api;

public record StudentImportRowResult(int row, boolean success, String message, Long studentId) {
}
