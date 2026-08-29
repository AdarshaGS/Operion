package com.operion.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
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
 * The inverse of every *TenantIsolationTest in this codebase (see e.g.
 * {@link StudentTenantIsolationTest}): those prove a school's own API surface never
 * sees another org's rows. This proves the platform-billing surface deliberately does
 * see across every org - Subscription/PlatformInvoice carry no @TenantId, so querying
 * them with no TenantContext set (exactly how a platform-admin request runs, per
 * PlatformAuthenticationInterceptor) returns rows for every organisation, not just one.
 * It also proves BillingService.generateInvoice's internal cross-tenant student count
 * still correctly isolates per org even though the platform-facing surface around it
 * does not - the two invoices below must reflect each org's own headcount, not a mixed
 * total.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, BillingService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BillingCrossOrgVisibilityTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private StudentRepository studentRepository;

	@Autowired
	private BillingService billingService;

	@Autowired
	private SubscriptionRepository subscriptionRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private Organisation newOrganisationWithActiveStudents(String slug, int studentCount) {
		Organisation organisation = organisationRepository.save(new Organisation(slug, slug + " Trust", slug));
		TenantContext.set(organisation.getId(), null);
		for (int i = 0; i < studentCount; i++) {
			Person person = personRepository.save(new Person(slug, "Student-" + i));
			Student student = new Student(person, "STU-" + slug + "-" + i, slug + "-" + i, LocalDate.of(2024, 6, 1), null, null, null, null, null, null, null, null, null, null, null);
			student.activate();
			studentRepository.save(student);
		}
		return organisation;
	}

	@Test
	void platformQueriesSeeSubscriptionsAcrossEveryOrganisationNotJustOne() {
		Organisation orgA = newOrganisationWithActiveStudents("cross-org-a-school", 2);
		Plan planA = billingService.createPlan("CROSS-A-" + orgA.getId(), "Plan A", new BigDecimal("100.00"));
		billingService.subscribe(orgA.getId(), planA.getId(), LocalDate.of(2026, 4, 1));

		Organisation orgB = newOrganisationWithActiveStudents("cross-org-b-school", 5);
		Plan planB = billingService.createPlan("CROSS-B-" + orgB.getId(), "Plan B", new BigDecimal("100.00"));
		billingService.subscribe(orgB.getId(), planB.getId(), LocalDate.of(2026, 4, 1));

		// No TenantContext set here - exactly the state a platform-admin request runs in.
		TenantContext.clear();
		List<Subscription> allSubscriptions = subscriptionRepository.findAll();

		assertThat(allSubscriptions.stream().map(s -> s.getOrganisation().getId()))
				.contains(orgA.getId(), orgB.getId());
	}

	@Test
	void invoiceGenerationIsolatesEachOrgsStudentCountEvenWhenComputedFromAPlatformRequest() {
		Organisation orgA = newOrganisationWithActiveStudents("headcount-a-school", 3);
		Plan planA = billingService.createPlan("HC-A-" + orgA.getId(), "Plan A", new BigDecimal("100.00"));
		billingService.subscribe(orgA.getId(), planA.getId(), LocalDate.of(2026, 4, 1));

		Organisation orgB = newOrganisationWithActiveStudents("headcount-b-school", 7);
		Plan planB = billingService.createPlan("HC-B-" + orgB.getId(), "Plan B", new BigDecimal("100.00"));
		billingService.subscribe(orgB.getId(), planB.getId(), LocalDate.of(2026, 4, 1));

		// Simulates the platform-admin plane: no org in TenantContext between calls.
		TenantContext.clear();
		PlatformInvoice invoiceA = billingService.generateInvoice(
				orgA.getId(), LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), LocalDate.of(2026, 4, 15));
		TenantContext.clear();
		PlatformInvoice invoiceB = billingService.generateInvoice(
				orgB.getId(), LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), LocalDate.of(2026, 4, 15));

		assertThat(invoiceA.getStudentCountAtBilling()).isEqualTo(3);
		assertThat(invoiceB.getStudentCountAtBilling()).isEqualTo(7);
	}
}
