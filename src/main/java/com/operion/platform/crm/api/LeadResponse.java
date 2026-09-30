package com.operion.platform.crm.api;

import java.time.Instant;

import com.operion.platform.crm.Lead;

public record LeadResponse(Long id, String schoolName, String source, String stage, Integer estimatedStudentCount,
		Long ownerId, String ownerName, String notes, Long convertedOrganisationId, Instant createdAt, Instant updatedAt) {

	public static LeadResponse from(Lead lead, String ownerName) {
		return new LeadResponse(lead.getId(), lead.getSchoolName(), lead.getSource().name(), lead.getStage().name(),
				lead.getEstimatedStudentCount(), lead.getOwnerId(), ownerName, lead.getNotes(),
				lead.getConvertedOrganisationId(), lead.getCreatedAt(), lead.getUpdatedAt());
	}
}
