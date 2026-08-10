package com.operion.communication.api;

import java.util.List;

import com.operion.communication.CommunicationService;
import com.operion.communication.NotificationChannel;
import com.operion.communication.NotificationTemplateRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notification-templates")
public class NotificationTemplateController {

	private final CommunicationService communicationService;
	private final NotificationTemplateRepository notificationTemplateRepository;

	public NotificationTemplateController(CommunicationService communicationService, NotificationTemplateRepository notificationTemplateRepository) {
		this.communicationService = communicationService;
		this.notificationTemplateRepository = notificationTemplateRepository;
	}

	@PostMapping
	public NotificationTemplateResponse create(@RequestBody CreateNotificationTemplateRequest request) {
		return NotificationTemplateResponse.from(communicationService.createTemplate(
				request.code(), NotificationChannel.valueOf(request.channel()), request.subjectTemplate(), request.bodyTemplate()));
	}

	@GetMapping
	public List<NotificationTemplateResponse> list() {
		return notificationTemplateRepository.findAll().stream().map(NotificationTemplateResponse::from).toList();
	}
}
