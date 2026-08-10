package com.operion.communication.api;

public record CreateNotificationTemplateRequest(String code, String channel, String subjectTemplate, String bodyTemplate) {
}
