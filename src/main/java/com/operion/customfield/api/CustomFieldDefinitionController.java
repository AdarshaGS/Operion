package com.operion.customfield.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.customfield.CustomFieldDataType;
import com.operion.customfield.CustomFieldDefinition;
import com.operion.customfield.CustomFieldDefinitionRepository;
import com.operion.customfield.CustomFieldDefinitionStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** No dedicated service - a custom field definition has no business rules beyond "save
 * the row", same as CampusController/GradeLevel's create+update+status shape. list() is
 * ungated (any authenticated member) since a record's own create/edit form (e.g.
 * StudentCreatePage) needs it to know which custom fields to render, same reasoning as
 * CampusController's list() populating a picker. */
@RestController
@RequestMapping("/api/v1/custom-field-definitions")
public class CustomFieldDefinitionController {

	private final CustomFieldDefinitionRepository definitionRepository;

	public CustomFieldDefinitionController(CustomFieldDefinitionRepository definitionRepository) {
		this.definitionRepository = definitionRepository;
	}

	@GetMapping
	public List<CustomFieldDefinitionResponse> list(@RequestParam(required = false) String entityType) {
		List<CustomFieldDefinition> definitions = entityType != null
				? definitionRepository.findByEntityType(entityType)
				: definitionRepository.findAll();
		return definitions.stream().map(CustomFieldDefinitionResponse::from).toList();
	}

	@PostMapping
	@RequirePermission("ORGANISATION_MANAGE")
	public CustomFieldDefinitionResponse create(@RequestBody CreateCustomFieldDefinitionRequest request) {
		CustomFieldDefinition definition = new CustomFieldDefinition(request.entityType(), request.fieldKey(), request.label(),
				CustomFieldDataType.valueOf(request.dataType()), request.options(), request.required());
		return CustomFieldDefinitionResponse.from(definitionRepository.save(definition));
	}

	@PatchMapping("/{id}")
	@RequirePermission("ORGANISATION_MANAGE")
	public CustomFieldDefinitionResponse update(@PathVariable Long id, @RequestBody UpdateCustomFieldDefinitionRequest request) {
		CustomFieldDefinition definition = definitionRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No custom field definition with id " + id));
		definition.update(request.label(), CustomFieldDataType.valueOf(request.dataType()), request.options(), request.required());
		return CustomFieldDefinitionResponse.from(definitionRepository.save(definition));
	}

	@PostMapping("/{id}/status")
	@RequirePermission("ORGANISATION_MANAGE")
	public CustomFieldDefinitionResponse changeStatus(@PathVariable Long id, @RequestBody ChangeStatusRequest request) {
		CustomFieldDefinition definition = definitionRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No custom field definition with id " + id));
		definition.changeStatus(CustomFieldDefinitionStatus.valueOf(request.status()));
		return CustomFieldDefinitionResponse.from(definitionRepository.save(definition));
	}
}
