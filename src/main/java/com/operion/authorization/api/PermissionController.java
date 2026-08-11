package com.operion.authorization.api;

import java.util.List;

import com.operion.authorization.PermissionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only global catalog, no @RequirePermission - any authenticated org member can list
 * it (needed to build a "assign permissions to a role" picker), same as Campus/AcademicYear
 * lookups elsewhere. Nothing here is org-specific or sensitive.
 */
@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionController {

	private final PermissionRepository permissionRepository;

	public PermissionController(PermissionRepository permissionRepository) {
		this.permissionRepository = permissionRepository;
	}

	@GetMapping
	public List<PermissionResponse> list() {
		return permissionRepository.findAll().stream().map(PermissionResponse::from).toList();
	}
}
