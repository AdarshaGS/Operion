package com.operion.organisation.api;

import com.operion.organisation.Department;

public record DepartmentResponse(Long id, String name, String status) {

	static DepartmentResponse from(Department department) {
		return new DepartmentResponse(department.getId(), department.getName(), department.getStatus().name());
	}
}
