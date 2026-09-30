package com.operion.learning;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.SectionStatus;
import com.operion.academic.Subject;
import com.operion.academic.SubjectRepository;
import com.operion.academic.SubjectStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the caller may pick in the learning forms: the sections they can see and, per section, the
 * subjects they may author content for. Lets one UI serve admins and teachers alike.
 */
@Service
@Transactional(readOnly = true)
public class LearningScopeService {

	public record SectionScope(Section section, List<Long> writableSubjectIds) {
	}

	public record Scope(boolean manager, List<SectionScope> sections, List<Subject> subjects) {
	}

	private final SectionRepository sectionRepository;
	private final SubjectRepository subjectRepository;

	public LearningScopeService(SectionRepository sectionRepository, SubjectRepository subjectRepository) {
		this.sectionRepository = sectionRepository;
		this.subjectRepository = subjectRepository;
	}

	public Scope scopeFor(LearningActor actor) {
		List<Subject> subjects = subjectRepository.findByStatus(SubjectStatus.ACTIVE);
		List<Long> allSubjectIds = subjects.stream().map(Subject::getId).toList();
		Set<Long> teachingSections = actor.teachingSectionIds();

		List<SectionScope> sections = new ArrayList<>();
		for (Section section : sectionRepository.findAll()) {
			if (section.getStatus() != SectionStatus.ACTIVE) {
				continue;
			}
			if (!actor.seesAllSections() && !teachingSections.contains(section.getId())) {
				continue;
			}
			List<Long> writable = actor.manager() ? allSubjectIds
					: allSubjectIds.stream().filter(id -> actor.canWrite(section.getId(), id)).collect(Collectors.toList());
			sections.add(new SectionScope(section, writable));
		}
		return new Scope(actor.manager(), sections, subjects);
	}
}
