package com.operion.organisation.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.organisation.Designation;
import com.operion.organisation.DesignationRepository;
import com.operion.organisation.DesignationStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** No dedicated service - Designation has no business rules beyond "save the row", same
 * as CampusController. */
@RestController
@RequestMapping("/api/v1/designations")
public class DesignationController {

	private final DesignationRepository designationRepository;

	public DesignationController(DesignationRepository designationRepository) {
		this.designationRepository = designationRepository;
	}

	@PostMapping
	@RequirePermission("ORGANISATION_MANAGE")
	public DesignationResponse create(@RequestBody CreateDesignationRequest request) {
		return DesignationResponse.from(designationRepository.save(new Designation(request.name())));
	}

	@GetMapping
	public List<DesignationResponse> list() {
		return designationRepository.findAll().stream().map(DesignationResponse::from).toList();
	}

	@PostMapping("/{id}/status")
	@RequirePermission("ORGANISATION_MANAGE")
	public DesignationResponse changeStatus(@PathVariable Long id, @RequestBody ChangeStatusRequest request) {
		Designation designation = designationRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No designation with id " + id));
		designation.changeStatus(DesignationStatus.valueOf(request.status()));
		return DesignationResponse.from(designationRepository.save(designation));
	}
}
