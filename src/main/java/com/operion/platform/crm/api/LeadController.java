package com.operion.platform.crm.api;

import java.util.List;
import java.util.Map;

import com.operion.organisation.api.CreateOrganisationRequest;
import com.operion.platform.crm.CrmDirectory;
import com.operion.platform.crm.Lead;
import com.operion.platform.crm.LeadService;
import com.operion.platform.crm.LeadSource;
import com.operion.platform.crm.LeadStage;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform CRM leads (milestone 32) - mounted under /api/v1/platform/**, gated purely by
 * PlatformAuthenticationInterceptor's URL prefix, same convention as every other platform
 * controller (see PlatformOrganisationController's javadoc). */
@RestController
@RequestMapping("/api/v1/platform/crm/leads")
public class LeadController {

	private final LeadService leadService;
	private final CrmDirectory crmDirectory;

	public LeadController(LeadService leadService, CrmDirectory crmDirectory) {
		this.leadService = leadService;
		this.crmDirectory = crmDirectory;
	}

	@GetMapping
	public List<LeadResponse> all() {
		List<Lead> leads = leadService.all();
		Map<Long, String> names = crmDirectory.namesOf(leads.stream().map(Lead::getOwnerId).toList());
		return leads.stream().map(lead -> LeadResponse.from(lead, names.get(lead.getOwnerId()))).toList();
	}

	@GetMapping("/{id}")
	public LeadResponse get(@PathVariable Long id) {
		return respond(leadService.get(id));
	}

	@PostMapping
	public LeadResponse create(@Valid @RequestBody CreateLeadRequest request) {
		return respond(leadService.create(request.schoolName(), LeadSource.valueOf(request.source()),
				request.estimatedStudentCount(), request.ownerId(), request.notes()));
	}

	@PatchMapping("/{id}")
	public LeadResponse update(@PathVariable Long id, @RequestBody UpdateLeadRequest request) {
		return respond(leadService.update(id, request.schoolName(),
				request.source() != null ? LeadSource.valueOf(request.source()) : null, request.estimatedStudentCount(),
				request.ownerId(), request.notes()));
	}

	@PatchMapping("/{id}/stage")
	public LeadResponse changeStage(@PathVariable Long id, @Valid @RequestBody ChangeLeadStageRequest request) {
		return respond(leadService.changeStage(id, LeadStage.valueOf(request.stage())));
	}

	/** Body is a full CreateOrganisationRequest (admin account, slug, ...); name and primary
	 * contact fall back to what the lead already knows. */
	@PostMapping("/{id}/convert")
	public LeadResponse convert(@PathVariable Long id, @RequestBody CreateOrganisationRequest request) {
		return respond(leadService.convert(id, request));
	}

	private LeadResponse respond(Lead lead) {
		return LeadResponse.from(lead, crmDirectory.namesOf(java.util.Collections.singletonList(lead.getOwnerId())).get(lead.getOwnerId()));
	}
}
