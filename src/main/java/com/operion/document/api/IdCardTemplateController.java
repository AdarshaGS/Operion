package com.operion.document.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.document.IdCardTemplate;
import com.operion.document.IdCardTemplateRepository;
import com.operion.document.IdCardTemplateService;
import com.operion.common.InvalidRequestException;
import jakarta.validation.Valid;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Every endpoint is permission-gated per #33's acceptance criteria: template design
 * (create) is an org-configuration action like OrganisationBrandingController's PUT, while
 * listing and rendering both expose student data (render resolves a real student's name,
 * admission number, class/section, and photo) so they're gated behind STUDENT_VIEW instead. */
@RestController
@RequestMapping("/api/v1/id-card-templates")
@RequirePermission("STUDENT_VIEW")
public class IdCardTemplateController {

	private final IdCardTemplateRepository idCardTemplateRepository;
	private final IdCardTemplateService idCardTemplateService;
	private final ObjectMapper objectMapper;

	public IdCardTemplateController(IdCardTemplateRepository idCardTemplateRepository, IdCardTemplateService idCardTemplateService,
			ObjectMapper objectMapper) {
		this.idCardTemplateRepository = idCardTemplateRepository;
		this.idCardTemplateService = idCardTemplateService;
		this.objectMapper = objectMapper;
	}

	@PostMapping
	@RequirePermission("ORGANISATION_MANAGE")
	public IdCardTemplateResponse create(@Valid @RequestBody CreateIdCardTemplateRequest request) {
		requireJsonObject(request.layoutJson());
		IdCardTemplate template = idCardTemplateRepository
				.save(new IdCardTemplate(request.name(), request.widthMm(), request.heightMm(), request.layoutJson()));
		return IdCardTemplateResponse.from(template);
	}

	/** The layout is parsed on every render, so a value that is not a JSON object is refused when it is saved. */
	private void requireJsonObject(String layoutJson) {
		try {
			if (!objectMapper.readTree(layoutJson).isObject()) {
				throw new InvalidRequestException("layoutJson must be a JSON object");
			}
		} catch (JacksonException e) {
			throw new InvalidRequestException("layoutJson is not valid JSON");
		}
	}

	@GetMapping
	public List<IdCardTemplateResponse> list() {
		return idCardTemplateRepository.findAll().stream().map(IdCardTemplateResponse::from).toList();
	}

	@PostMapping("/{id}/render")
	public IdCardRenderResponse render(@PathVariable Long id, @RequestParam Long studentId) {
		return idCardTemplateService.render(id, studentId);
	}
}
