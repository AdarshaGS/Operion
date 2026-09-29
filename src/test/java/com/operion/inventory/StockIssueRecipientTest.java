package com.operion.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Department;
import com.operion.organisation.DepartmentRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.student.Student;
import com.operion.student.StudentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Covers #174: StockIssue's optional structured recipient links (department, staff
 * Person, student) alongside the existing required free-text issuedTo. Mutual
 * exclusivity between the three is validated in StockIssueController, not here - same
 * "no dedicated service" split as CustomerController's student/guardian check.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, InventoryService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StockIssueRecipientTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private ItemCategoryRepository itemCategoryRepository;

	@Autowired
	private DepartmentRepository departmentRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private StudentRepository studentRepository;

	@Autowired
	private InventoryService inventoryService;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(Item item, Campus campus) {
	}

	private Fixture setUpFixture(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);

		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		ItemCategory category = itemCategoryRepository.save(new ItemCategory("STATIONERY", "Stationery", null));
		Item item = inventoryService.createItem(category, "ITEM-001", "A4 Paper Ream", "REAM", null, null);
		inventoryService.recordEntry(item, campus, 50, null, LocalDate.of(2026, 1, 1), "Vendor A", null);

		return new Fixture(item, campus);
	}

	@Test
	void issueWithoutStructuredRecipientLeavesLinksNull() {
		Fixture fixture = setUpFixture("stock-issue-recipient-none");

		StockIssue issue = inventoryService.recordIssue(fixture.item(), fixture.campus(), 5, LocalDate.of(2026, 1, 5), "Science Lab",
				"Practicals", null);

		assertThat(issue.getDepartment()).isNull();
		assertThat(issue.getRecipientPerson()).isNull();
		assertThat(issue.getRecipientStudent()).isNull();
	}

	@Test
	void issueCanLinkToADepartment() {
		Fixture fixture = setUpFixture("stock-issue-recipient-department");
		Department accounts = departmentRepository.save(new Department("Accounts"));

		StockIssue issue = inventoryService.recordIssue(fixture.item(), fixture.campus(), 5, LocalDate.of(2026, 1, 5), "Accounts department",
				null, null, accounts, null, null, null);

		assertThat(issue.getDepartment().getId()).isEqualTo(accounts.getId());
		assertThat(issue.getRecipientPerson()).isNull();
		assertThat(issue.getRecipientStudent()).isNull();
	}

	@Test
	void issueCanLinkToAStaffMember() {
		Fixture fixture = setUpFixture("stock-issue-recipient-staff");
		Person staff = personRepository.save(new Person("Meera", "Nair"));

		StockIssue issue = inventoryService.recordIssue(fixture.item(), fixture.campus(), 5, LocalDate.of(2026, 1, 5), "Meera Nair", null, null,
				null, staff, null, null);

		assertThat(issue.getRecipientPerson().getId()).isEqualTo(staff.getId());
		assertThat(issue.getDepartment()).isNull();
		assertThat(issue.getRecipientStudent()).isNull();
	}

	@Test
	void issueCanLinkToAStudent() {
		Fixture fixture = setUpFixture("stock-issue-recipient-student");
		Person studentPerson = personRepository.save(new Person("Ira", "Shah"));
		Student student = studentRepository.save(new Student(studentPerson, "STU-2026-001", "ADM-001", LocalDate.of(2026, 1, 1), null, null,
				null, null, null, null, null, null, null, null, null, null));

		StockIssue issue = inventoryService.recordIssue(fixture.item(), fixture.campus(), 5, LocalDate.of(2026, 1, 5), "Ira Shah", null, null,
				null, null, student, null);

		assertThat(issue.getRecipientStudent().getId()).isEqualTo(student.getId());
		assertThat(issue.getDepartment()).isNull();
		assertThat(issue.getRecipientPerson()).isNull();
	}
}
