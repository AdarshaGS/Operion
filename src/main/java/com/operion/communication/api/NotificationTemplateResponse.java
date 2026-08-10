package com.operion.communication.api;

import com.operion.communication.NotificationTemplate;

public record NotificationTemplateResponse(Long id, String code, String channel, String subjectTemplate, String bodyTemplate) {

	static NotificationTemplateResponse from(NotificationTemplate template) {
		return new NotificationTemplateResponse(template.getId(), template.getCode(), template.getChannel().name(),
				template.getSubjectTemplate(), template.getBodyTemplate());
	}
}
