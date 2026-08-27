package com.operion.organisation.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.organisation.Department;
import com.operion.organisation.DepartmentRepository;
import com.operion.organisation.DepartmentStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** No dedicated service - Department has no business rules beyond "save the row", same
 * as CampusController. */
@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

	private final DepartmentRepository departmentRepository;

	public DepartmentController(DepartmentRepository departmentRepository) {
		this.departmentRepository = departmentRepository;
	}

	@PostMapping
	@RequirePermission("ORGANISATION_MANAGE")
	public DepartmentResponse create(@RequestBody CreateDepartmentRequest request) {
		return DepartmentResponse.from(departmentRepository.save(new Department(request.name())));
	}

	@GetMapping
	public List<DepartmentResponse> list() {
		return departmentRepository.findAll().stream().map(DepartmentResponse::from).toList();
	}

	@PostMapping("/{id}/status")
	@RequirePermission("ORGANISATION_MANAGE")
	public DepartmentResponse changeStatus(@PathVariable Long id, @RequestBody ChangeStatusRequest request) {
		Department department = departmentRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No department with id " + id));
		department.changeStatus(DepartmentStatus.valueOf(request.status()));
		return DepartmentResponse.from(departmentRepository.save(department));
	}
}
