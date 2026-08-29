package com.operion.academic.api;

import com.operion.academic.Subject;

public record SubjectResponse(Long id, String name, String code, String status) {

	static SubjectResponse from(Subject subject) {
		return new SubjectResponse(subject.getId(), subject.getName(), subject.getCode(), subject.getStatus().name());
	}
}
