package com.operion.platform.crm;

import java.time.Instant;

import com.operion.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A follow-up reminder on a lead or organisation. Named CrmTask (table crm_tasks) so it
 * can't be confused with any other "task" concept in the codebase. */
@Getter
@Entity
@Table(name = "crm_tasks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CrmTask extends BaseEntity {

	@Column(name = "lead_id", updatable = false)
	private Long leadId;

	@Column(name = "organisation_id", updatable = false)
	private Long organisationId;

	@Column(nullable = false)
	private String title;

	@Column(name = "due_at", nullable = false)
	private Instant dueAt;

	@Column(name = "assignee_id")
	private Long assigneeId;

	@Column(name = "completed_at")
	private Instant completedAt;

	public CrmTask(CrmOwner owner, String title, Instant dueAt, Long assigneeId) {
		this.leadId = owner.leadId();
		this.organisationId = owner.organisationId();
		update(title, dueAt, assigneeId);
	}

	public void update(String title, Instant dueAt, Long assigneeId) {
		this.title = title;
		this.dueAt = dueAt;
		this.assigneeId = assigneeId;
	}

	public boolean isCompleted() {
		return completedAt != null;
	}

	/** Idempotent - completing an already-completed task keeps its original completion time. */
	public void complete(Instant when) {
		if (completedAt == null) {
			this.completedAt = when;
		}
	}
}
