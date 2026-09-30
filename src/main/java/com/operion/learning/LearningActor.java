package com.operion.learning;

import java.util.List;
import java.util.Set;

import com.operion.authorization.AuthorizationDeniedException;

/**
 * The caller as the learning module sees them. Learning endpoints are not gated by a fixed
 * permission code because the same endpoints serve two audiences: admins (LEARNING_MANAGE /
 * LEARNING_VIEW, all sections) and teachers on the portal, who carry no such codes and are scoped
 * to the sections they are actually assigned to teach (same approach as the /api/v1/me/**
 * endpoints, see LearningAccessService).
 */
public record LearningActor(Long userId, Long personId, boolean manager, boolean viewer, boolean reviewer,
		List<Teaching> teaching) {

	/** subjectId is null for a homeroom assignment, which covers every subject in the section. */
	public record Teaching(Long sectionId, Long subjectId) {
	}

	public boolean canReadSection(Long sectionId) {
		return manager || viewer || teaches(sectionId);
	}

	public boolean canWrite(Long sectionId, Long subjectId) {
		return manager || teaching.stream().anyMatch(t -> t.sectionId().equals(sectionId)
				&& (t.subjectId() == null || t.subjectId().equals(subjectId)));
	}

	/** May author subject-level content (e.g. question bank) - teaches the subject anywhere, or is homeroom somewhere. */
	public boolean canAuthorSubject(Long subjectId) {
		return manager || teaching.stream().anyMatch(t -> t.subjectId() == null || t.subjectId().equals(subjectId));
	}

	public boolean teaches(Long sectionId) {
		return teaching.stream().anyMatch(t -> t.sectionId().equals(sectionId));
	}

	/** True when the caller may see every section, so no per-section filtering applies. */
	public boolean seesAllSections() {
		return manager || viewer;
	}

	public Set<Long> teachingSectionIds() {
		return teaching.stream().map(Teaching::sectionId).collect(java.util.stream.Collectors.toSet());
	}

	public void requireWrite(Long sectionId, Long subjectId) {
		if (!canWrite(sectionId, subjectId)) {
			throw new AuthorizationDeniedException("You are not assigned to teach this section and subject");
		}
	}

	public void requireReadSection(Long sectionId) {
		if (!canReadSection(sectionId)) {
			throw new AuthorizationDeniedException("You have no access to this section");
		}
	}

	public void requireReviewer() {
		if (!reviewer) {
			throw new AuthorizationDeniedException("Missing required permission: LESSON_PLAN_REVIEW");
		}
	}

	public void requireManager() {
		if (!manager) {
			throw new AuthorizationDeniedException("Missing required permission: LEARNING_MANAGE");
		}
	}
}
