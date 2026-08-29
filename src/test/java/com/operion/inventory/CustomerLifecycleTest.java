package com.operion.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.parent.Guardian;
import com.operion.parent.GuardianRepository;
import com.operion.student.Student;
import com.operion.student.StudentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Covers #52 - Customer supports a walk-in (no link at all) and an existing
 * Student/Guardian linked for purchase-history continuity, same optional-FK shape either
 * way. Controller-level validation (student and guardian mutually exclusive) is a
 * one-line guard clause, not covered here - same proportion of test investment as
 * Supplier/Department's equally simple controllers. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CustomerLifecycleTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private StudentRepository studentRepository;

	@Autowired
	private GuardianRepository guardianRepository;

	@Autowired
	private CustomerRepository customerRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private void newTenant(String slugPrefix) {
		Organisation organisation = organisationRepository.save(
				new Organisation("Test School", "Test School Trust", slugPrefix + "-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);
	}

	@Test
	void aWalkInCustomerHasNoStudentOrGuardianLink() {
		newTenant("walk-in");

		Customer customer = customerRepository.save(new Customer(null, null, "Cash Customer", "9999999999"));

		assertThat(customer.getStudent()).isNull();
		assertThat(customer.getGuardian()).isNull();
		assertThat(customer.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
	}

	@Test
	void aCustomerCanLinkToAnExistingStudent() {
		newTenant("student-link");
		Person person = personRepository.save(new Person("Riya", "Sharma"));
		Student student = studentRepository
				.save(new Student(person, "STU-ADM-001", "ADM-001", LocalDate.now(), null, null, null, null, null, null, null, null, null, null, null));

		Customer customer = customerRepository.save(new Customer(student, null, "Riya Sharma", null));

		assertThat(customer.getStudent().getId()).isEqualTo(student.getId());
		assertThat(customer.getGuardian()).isNull();
	}

	@Test
	void aCustomerCanLinkToAnExistingGuardian() {
		newTenant("guardian-link");
		Person person = personRepository.save(new Person("Amit", "Sharma"));
		Guardian guardian = guardianRepository.save(new Guardian(person, "Engineer"));

		Customer customer = customerRepository.save(new Customer(null, guardian, "Amit Sharma", null));

		assertThat(customer.getGuardian().getId()).isEqualTo(guardian.getId());
		assertThat(customer.getStudent()).isNull();
	}

	@Test
	void changingStatusPersists() {
		newTenant("toggle");
		Customer customer = customerRepository.save(new Customer(null, null, "Cash Customer", null));

		customer.changeStatus(CustomerStatus.INACTIVE);
		customerRepository.save(customer);

		assertThat(customerRepository.findById(customer.getId()).orElseThrow().getStatus()).isEqualTo(CustomerStatus.INACTIVE);
	}

	@Test
	void customersAreTenantScoped() {
		newTenant("tenant-a");
		customerRepository.save(new Customer(null, null, "Org A Customer", null));
		TenantContext.clear();

		newTenant("tenant-b");
		assertThat(customerRepository.findAll()).isEmpty();
	}
}
