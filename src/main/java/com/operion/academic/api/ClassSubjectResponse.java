package com.operion.academic.api;

import com.operion.academic.ClassSubject;

public record ClassSubjectResponse(Long id, Long schoolClassId, Long subjectId, boolean mandatory, String status) {

	static ClassSubjectResponse from(ClassSubject classSubject) {
		return new ClassSubjectResponse(classSubject.getId(), classSubject.getSchoolClass().getId(),
				classSubject.getSubject().getId(), classSubject.isMandatory(), classSubject.getStatus().name());
	}
}
