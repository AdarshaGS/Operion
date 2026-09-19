package com.operion.support.api;

import java.time.Instant;

import com.operion.support.SupportTicket;

public record SupportTicketResponse(Long id, Long organisationId, String subject, String description, String status,
		String priority, String requesterEmail, String assigneeEmail, Instant createdAt) {

	public static SupportTicketResponse from(SupportTicket ticket) {
		return new SupportTicketResponse(ticket.getId(), ticket.getOrganisation().getId(), ticket.getSubject(),
				ticket.getDescription(), ticket.getStatus().name(), ticket.getPriority().name(), ticket.getRequesterEmail(),
				ticket.getAssigneeEmail(), ticket.getCreatedAt());
	}
}
