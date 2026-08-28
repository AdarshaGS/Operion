package com.operion.inventory.api;

import com.operion.inventory.Customer;

public record CustomerResponse(Long id, Long studentId, Long guardianId, String name, String phone, String status) {

	public static CustomerResponse from(Customer customer) {
		return new CustomerResponse(customer.getId(), customer.getStudent() == null ? null : customer.getStudent().getId(),
				customer.getGuardian() == null ? null : customer.getGuardian().getId(), customer.getName(), customer.getPhone(),
				customer.getStatus().name());
	}
}
