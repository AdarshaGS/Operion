package com.operion.platform.crm.api;

/** Every field optional - null leaves the current value unchanged. */
public record UpdateLeadRequest(String schoolName, String source, Integer estimatedStudentCount, Long ownerId, String notes) {
}
