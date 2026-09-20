package com.operion.hr.api;

import com.operion.academic.Subject;

/** A minimal, deliberately narrow view of Subject for the public careers form (#286) - just
 * enough to populate a dropdown, none of the fields an authenticated SubjectResponse
 * exposes. */
public record PublicSubjectResponse(Long id, String name) {

	static PublicSubjectResponse from(Subject subject) {
		return new PublicSubjectResponse(subject.getId(), subject.getName());
	}
}
