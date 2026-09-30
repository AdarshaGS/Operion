package com.operion.platform.crm.api;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.operion.platform.crm.CrmDirectory;
import com.operion.platform.crm.CrmTask;
import com.operion.platform.crm.CrmTaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/crm/tasks")
public class CrmTaskController {

	private final CrmTaskService crmTaskService;
	private final CrmDirectory crmDirectory;

	public CrmTaskController(CrmTaskService crmTaskService, CrmDirectory crmDirectory) {
		this.crmTaskService = crmTaskService;
		this.crmDirectory = crmDirectory;
	}

	@GetMapping
	public List<CrmTaskResponse> list(@RequestParam(required = false) Long leadId,
			@RequestParam(required = false) Long organisationId, @RequestParam(required = false) Long assigneeId,
			@RequestParam(defaultValue = "false") boolean open) {
		List<CrmTask> tasks = crmTaskService.list(leadId, organisationId, assigneeId, open);
		Map<Long, String> names = crmDirectory.namesOf(tasks.stream().map(CrmTask::getAssigneeId).toList());
		return tasks.stream().map(task -> CrmTaskResponse.from(task, names.get(task.getAssigneeId()))).toList();
	}

	@PostMapping
	public CrmTaskResponse create(@Valid @RequestBody CreateCrmTaskRequest request) {
		return respond(crmTaskService.create(request.leadId(), request.organisationId(), request.title(), request.dueAt(),
				request.assigneeId()));
	}

	@PatchMapping("/{id}")
	public CrmTaskResponse update(@PathVariable Long id, @RequestBody UpdateCrmTaskRequest request) {
		return respond(crmTaskService.update(id, request.title(), request.dueAt(), request.assigneeId()));
	}

	@PostMapping("/{id}/complete")
	public CrmTaskResponse complete(@PathVariable Long id) {
		return respond(crmTaskService.complete(id));
	}

	private CrmTaskResponse respond(CrmTask task) {
		return CrmTaskResponse.from(task,
				crmDirectory.namesOf(Collections.singletonList(task.getAssigneeId())).get(task.getAssigneeId()));
	}
}
