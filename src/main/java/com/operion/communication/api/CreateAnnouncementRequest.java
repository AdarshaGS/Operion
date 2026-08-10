package com.operion.communication.api;

public record CreateAnnouncementRequest(Long campusId, String title, String body, String audienceType, Long audienceRefId) {
}
