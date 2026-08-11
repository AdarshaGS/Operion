package com.operion.organisation.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** No dedicated service - Campus has no business rules beyond "save the row" at create time (a provisioned org's first
 * campus is created directly by OrganisationService; this controller covers admin-driven additional campuses). */
@RestController
@RequestMapping("/api/v1/campuses")
public class CampusController {

	private final CampusRepository campusRepository;

	public CampusController(CampusRepository campusRepository) {
		this.campusRepository = campusRepository;
	}

	@PostMapping
	@RequirePermission("ORGANISATION_MANAGE")
	public CampusResponse create(@RequestBody CreateCampusRequest request) {
		Campus campus = new Campus(request.name(), request.code());
		campus.setAddressLine1(request.addressLine1());
		campus.setAddressLine2(request.addressLine2());
		campus.setCity(request.city());
		campus.setState(request.state());
		campus.setPincode(request.pincode());
		campus.setTimezone(request.timezone());
		return CampusResponse.from(campusRepository.save(campus));
	}

	@GetMapping
	public List<CampusResponse> list() {
		return campusRepository.findAll().stream().map(CampusResponse::from).toList();
	}
}
