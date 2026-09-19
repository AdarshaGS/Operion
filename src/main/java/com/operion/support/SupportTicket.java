package com.operion.support;

import com.operion.common.BaseEntity;
import com.operion.organisation.Organisation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Platform admin's cross-org ticket - not organisation-scoped (extends BaseEntity, not
 * TenantScopedEntity), same "the platform plane is the one place cross-tenant visibility
 * is allowed" convention as Plan/Subscription/PlatformInvoice. MVP: a single description
 * field stands in for a message thread - see the tracking issue for the full-thread
 * follow-up.
 */
@Getter
@Entity
@Table(name = "support_tickets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportTicket extends BaseEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "organisation_id")
	private Organisation organisation;

	@Column(nullable = false)
	private String subject;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SupportTicketStatus status;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SupportTicketPriority priority;

	@Column(name = "requester_email", nullable = false)
	private String requesterEmail;

	/** Nullable - unassigned until a platform admin picks it up. */
	@Column(name = "assignee_email")
	private String assigneeEmail;

	public SupportTicket(Organisation organisation, String subject, String description, SupportTicketPriority priority,
			String requesterEmail) {
		this.organisation = organisation;
		this.subject = subject;
		this.description = description;
		this.status = SupportTicketStatus.OPEN;
		this.priority = priority;
		this.requesterEmail = requesterEmail;
	}

	public void changeStatus(SupportTicketStatus target) {
		this.status = target;
	}

	public void assign(String assigneeEmail) {
		this.assigneeEmail = assigneeEmail;
	}
}
