package com.operion.inventory.api;

import com.operion.inventory.Supplier;

public record SupplierResponse(Long id, String name, String contactPerson, String phone, String email, String address, String status) {

	public static SupplierResponse from(Supplier supplier) {
		return new SupplierResponse(supplier.getId(), supplier.getName(), supplier.getContactPerson(), supplier.getPhone(),
				supplier.getEmail(), supplier.getAddress(), supplier.getStatus().name());
	}
}
