package com.operion.learning.api;

import java.util.List;

import com.operion.common.TenantContext;
import com.operion.learning.LearningAccessService;
import com.operion.learning.LearningScopeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning/scope")
public class LearningScopeController {

	public record SectionOption(Long sectionId, Long classId, String className, String sectionName, List<Long> writableSubjectIds) {
	}

	public record SubjectOption(Long id, String name) {
	}

	public record ScopeResponse(boolean manager, List<SectionOption> sections, List<SubjectOption> subjects) {
	}

	private final LearningAccessService accessService;
	private final LearningScopeService scopeService;

	public LearningScopeController(LearningAccessService accessService, LearningScopeService scopeService) {
		this.accessService = accessService;
		this.scopeService = scopeService;
	}

	@GetMapping
	public ScopeResponse scope() {
		var scope = scopeService.scopeFor(accessService.resolve(TenantContext.getActorId()));
		return new ScopeResponse(scope.manager(),
				scope.sections().stream().map(s -> new SectionOption(s.section().getId(), s.section().getSchoolClass().getId(),
						com.operion.learning.Labels.className(s.section()), s.section().getName(), s.writableSubjectIds())).toList(),
				scope.subjects().stream().map(s -> new SubjectOption(s.getId(), s.getName())).toList());
	}
}
