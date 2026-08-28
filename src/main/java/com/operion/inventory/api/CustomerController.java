package com.operion.inventory.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.inventory.Customer;
import com.operion.inventory.CustomerRepository;
import com.operion.inventory.CustomerStatus;
import com.operion.parent.Guardian;
import com.operion.parent.GuardianRepository;
import com.operion.student.Student;
import com.operion.student.StudentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** No dedicated service - Customer has one validation rule (at most one of
 * student/guardian linked) beyond "save the row", checked directly here same as
 * Supplier/Department's shape. */
@RestController
@RequestMapping("/api/v1/inventory/customers")
@RequirePermission("INVENTORY_VIEW")
public class CustomerController {

	private final CustomerRepository customerRepository;
	private final StudentRepository studentRepository;
	private final GuardianRepository guardianRepository;

	public CustomerController(CustomerRepository customerRepository, StudentRepository studentRepository,
			GuardianRepository guardianRepository) {
		this.customerRepository = customerRepository;
		this.studentRepository = studentRepository;
		this.guardianRepository = guardianRepository;
	}

	@PostMapping
	@RequirePermission("INVENTORY_CUSTOMER_MANAGE")
	public CustomerResponse create(@RequestBody CreateCustomerRequest request) {
		if (request.studentId() != null && request.guardianId() != null) {
			throw new IllegalArgumentException("A customer can link to a student or a guardian, not both");
		}
		Student student = request.studentId() == null ? null : studentRepository.findById(request.studentId())
				.orElseThrow(() -> new IllegalArgumentException("No student with id " + request.studentId()));
		Guardian guardian = request.guardianId() == null ? null : guardianRepository.findById(request.guardianId())
				.orElseThrow(() -> new IllegalArgumentException("No guardian with id " + request.guardianId()));

		Customer customer = customerRepository.save(new Customer(student, guardian, request.name(), request.phone()));
		return CustomerResponse.from(customer);
	}

	/** Both statuses - same toggle-both-ways reasoning as SupplierController.list(). */
	@GetMapping
	public List<CustomerResponse> list() {
		return customerRepository.findAll().stream().map(CustomerResponse::from).toList();
	}

	@PostMapping("/{id}/status")
	@RequirePermission("INVENTORY_CUSTOMER_MANAGE")
	public CustomerResponse changeStatus(@PathVariable Long id, @RequestBody ChangeCustomerStatusRequest request) {
		Customer customer = findOrThrow(id);
		customer.changeStatus(CustomerStatus.valueOf(request.status()));
		return CustomerResponse.from(customerRepository.save(customer));
	}

	private Customer findOrThrow(Long id) {
		return customerRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No customer with id " + id));
	}
}
