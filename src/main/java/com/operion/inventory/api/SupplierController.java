package com.operion.inventory.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.inventory.Supplier;
import com.operion.inventory.SupplierRepository;
import com.operion.inventory.SupplierStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** No dedicated service - Supplier has no business rules beyond "save the row", same as
 * DepartmentController/DesignationController. */
@RestController
@RequestMapping("/api/v1/inventory/suppliers")
@RequirePermission("INVENTORY_VIEW")
public class SupplierController {

	private final SupplierRepository supplierRepository;

	public SupplierController(SupplierRepository supplierRepository) {
		this.supplierRepository = supplierRepository;
	}

	@PostMapping
	@RequirePermission("INVENTORY_SUPPLIER_MANAGE")
	public SupplierResponse create(@RequestBody CreateSupplierRequest request) {
		Supplier supplier = supplierRepository
				.save(new Supplier(request.name(), request.contactPerson(), request.phone(), request.email(), request.address()));
		return SupplierResponse.from(supplier);
	}

	/** Both statuses, unlike ItemCategoryController's ACTIVE-only list() - Supplier follows
	 * Department's toggle-both-ways pattern instead, which needs INACTIVE rows visible too
	 * so they can be reactivated. */
	@GetMapping
	public List<SupplierResponse> list() {
		return supplierRepository.findAll().stream().map(SupplierResponse::from).toList();
	}

	@PostMapping("/{id}/status")
	@RequirePermission("INVENTORY_SUPPLIER_MANAGE")
	public SupplierResponse changeStatus(@PathVariable Long id, @RequestBody ChangeSupplierStatusRequest request) {
		Supplier supplier = findOrThrow(id);
		supplier.changeStatus(SupplierStatus.valueOf(request.status()));
		return SupplierResponse.from(supplierRepository.save(supplier));
	}

	private Supplier findOrThrow(Long id) {
		return supplierRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No supplier with id " + id));
	}
}
