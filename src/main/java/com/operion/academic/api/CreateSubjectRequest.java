package com.operion.academic.api;

public record CreateSubjectRequest(String name, String code, boolean elective) {
}
