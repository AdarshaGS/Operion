package com.operion.platform.crm;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import com.operion.common.persistence.PlatformTransactional;

@Service
public class CrmTaskService {

	private final CrmTaskRepository crmTaskRepository;
	private final CrmDirectory crmDirectory;

	public CrmTaskService(CrmTaskRepository crmTaskRepository, CrmDirectory crmDirectory) {
		this.crmTaskRepository = crmTaskRepository;
		this.crmDirectory = crmDirectory;
	}

	/** All filters optional; with neither leadId nor organisationId this is every task, soonest due first. */
	public List<CrmTask> list(Long leadId, Long organisationId, Long assigneeId, boolean openOnly) {
		List<CrmTask> tasks;
		if (leadId != null || organisationId != null) {
			CrmOwner owner = crmDirectory.owner(leadId, organisationId);
			tasks = owner.leadId() != null ? crmTaskRepository.findByLeadIdOrderByDueAtAsc(owner.leadId())
					: crmTaskRepository.findByOrganisationIdOrderByDueAtAsc(owner.organisationId());
		} else {
			tasks = crmTaskRepository.findAllByOrderByDueAtAsc();
		}
		return tasks.stream()
				.filter(task -> !openOnly || !task.isCompleted())
				.filter(task -> assigneeId == null || assigneeId.equals(task.getAssigneeId()))
				.toList();
	}

	/** An unspecified assignee defaults to the platform admin creating the task. */
	@PlatformTransactional
	public CrmTask create(Long leadId, Long organisationId, String title, Instant dueAt, Long assigneeId) {
		CrmOwner owner = crmDirectory.owner(leadId, organisationId);
		Long assignee = assigneeId != null ? assigneeId : crmDirectory.currentAdminId();
		crmDirectory.requireAdmin(assignee);
		return crmTaskRepository.save(new CrmTask(owner, title.trim(), dueAt, assignee));
	}

	/** Partial update: a null field leaves the current value alone. */
	@PlatformTransactional
	public CrmTask update(Long id, String title, Instant dueAt, Long assigneeId) {
		CrmTask task = get(id);
		crmDirectory.requireAdmin(assigneeId);
		task.update(title != null && !title.isBlank() ? title.trim() : task.getTitle(),
				dueAt != null ? dueAt : task.getDueAt(),
				assigneeId != null ? assigneeId : task.getAssigneeId());
		return crmTaskRepository.save(task);
	}

	@PlatformTransactional
	public CrmTask complete(Long id) {
		CrmTask task = get(id);
		task.complete(Instant.now());
		return crmTaskRepository.save(task);
	}

	private CrmTask get(Long id) {
		return crmTaskRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No task with id " + id));
	}
}
