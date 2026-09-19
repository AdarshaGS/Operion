package com.operion.support.api;

import java.util.List;

import com.operion.support.SupportTicketPriority;
import com.operion.support.SupportTicketService;
import com.operion.support.SupportTicketStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Support / Issues (#277) - mounted under /api/v1/platform/**, gated purely by
 * PlatformAuthenticationInterceptor's URL prefix, same convention as every other
 * platform controller (see PlatformOrganisationController's javadoc). */
@RestController
@RequestMapping("/api/v1/platform/support-tickets")
public class SupportTicketController {

	private final SupportTicketService supportTicketService;

	public SupportTicketController(SupportTicketService supportTicketService) {
		this.supportTicketService = supportTicketService;
	}

	@GetMapping
	public List<SupportTicketResponse> all() {
		return supportTicketService.all().stream().map(SupportTicketResponse::from).toList();
	}

	@GetMapping("/{id}")
	public SupportTicketResponse get(@PathVariable Long id) {
		return SupportTicketResponse.from(supportTicketService.get(id));
	}

	@PostMapping
	public SupportTicketResponse create(@RequestBody CreateSupportTicketRequest request) {
		return SupportTicketResponse.from(supportTicketService.create(request.organisationId(), request.subject(),
				request.description(), SupportTicketPriority.valueOf(request.priority()), request.requesterEmail()));
	}

	@PatchMapping("/{id}/status")
	public SupportTicketResponse changeStatus(@PathVariable Long id, @RequestBody ChangeSupportTicketStatusRequest request) {
		return SupportTicketResponse.from(supportTicketService.changeStatus(id, SupportTicketStatus.valueOf(request.status())));
	}

	@PatchMapping("/{id}/assignee")
	public SupportTicketResponse assign(@PathVariable Long id, @RequestBody AssignSupportTicketRequest request) {
		return SupportTicketResponse.from(supportTicketService.assign(id, request.assigneeEmail()));
	}
}
