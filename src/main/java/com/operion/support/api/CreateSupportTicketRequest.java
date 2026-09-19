package com.operion.support.api;

public record CreateSupportTicketRequest(Long organisationId, String subject, String description, String priority,
		String requesterEmail) {
}
