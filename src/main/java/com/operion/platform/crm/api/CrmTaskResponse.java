package com.operion.platform.crm.api;

import java.time.Instant;

import com.operion.platform.crm.CrmTask;

public record CrmTaskResponse(Long id, Long leadId, Long organisationId, String title, Instant dueAt, Long assigneeId,
		String assigneeName, Instant completedAt) {

	public static CrmTaskResponse from(CrmTask task, String assigneeName) {
		return new CrmTaskResponse(task.getId(), task.getLeadId(), task.getOrganisationId(), task.getTitle(), task.getDueAt(),
				task.getAssigneeId(), assigneeName, task.getCompletedAt());
	}
}
