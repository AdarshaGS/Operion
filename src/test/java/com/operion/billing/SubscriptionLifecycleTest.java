package com.operion.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * Proves BillingService's core rules: one ACTIVE subscription per org (changing plans
 * ends the old row and inserts a new one, insert-only history same as
 * TeacherAssignment), an invoice can't be generated without an active subscription, the
 * invoiced amount is computed from the org's live ACTIVE student headcount at the
 * subscription's snapshotted rate, and markPaid is a one-way transition.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, BillingService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SubscriptionLifecycleTest {

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

	private Organisation newOrganisation(String slug) {
		return organisationRepository.save(new Organisation(slug, slug + " Trust", slug));
	}

	private void addActiveStudent(String admissionNumber) {
		Person person = personRepository.save(new Person("Student", admissionNumber));
		Student student = new Student(person, "STU-" + admissionNumber, admissionNumber, LocalDate.of(2024, 6, 1), null, null, null, null, null, null, null, null, null, null, null);
		student.activate();
		studentRepository.save(student);
	}

	@Test
	void subscribingAgainEndsThePriorSubscriptionAndKeepsHistory() {
		Organisation organisation = newOrganisation("sub-history-school");
		TenantContext.set(organisation.getId(), null);
		Plan basic = billingService.createPlan("BASIC-" + organisation.getId(), "Basic", new BigDecimal("100.00"));
		Plan premium = billingService.createPlan("PREMIUM-" + organisation.getId(), "Premium", new BigDecimal("200.00"));

		Subscription first = billingService.subscribe(organisation.getId(), basic.getId(), LocalDate.of(2025, 4, 1));
		Subscription second = billingService.subscribe(organisation.getId(), premium.getId(), LocalDate.of(2026, 4, 1));

		List<Subscription> history = billingService.subscriptionHistory(organisation.getId());
		assertThat(history).hasSize(2);

		Subscription reloadedFirst = subscriptionRepository.findById(first.getId()).orElseThrow();
		assertThat(reloadedFirst.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
		assertThat(reloadedFirst.getEndDate()).isEqualTo(LocalDate.of(2026, 4, 1));

		Subscription reloadedSecond = subscriptionRepository.findById(second.getId()).orElseThrow();
		assertThat(reloadedSecond.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
		assertThat(reloadedSecond.getPricePerStudentPerYear()).isEqualByComparingTo("200.00");
	}

	@Test
	void generatingAnInvoiceWithoutAnActiveSubscriptionFails() {
		Organisation organisation = newOrganisation("no-sub-school");
		TenantContext.set(organisation.getId(), null);

		assertThatThrownBy(() -> billingService.generateInvoice(
				organisation.getId(), LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), LocalDate.of(2026, 4, 15)))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void invoiceAmountIsComputedFromTheLiveActiveStudentCountAtTheSubscribedRate() {
		Organisation organisation = newOrganisation("headcount-school");
		TenantContext.set(organisation.getId(), null);
		addActiveStudent("HC-1");
		addActiveStudent("HC-2");
		addActiveStudent("HC-3");

		Plan plan = billingService.createPlan("RATE-" + organisation.getId(), "Rate Plan", new BigDecimal("150.00"));
		billingService.subscribe(organisation.getId(), plan.getId(), LocalDate.of(2026, 4, 1));

		PlatformInvoice invoice = billingService.generateInvoice(
				organisation.getId(), LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), LocalDate.of(2026, 4, 15));

		assertThat(invoice.getStudentCountAtBilling()).isEqualTo(3);
		assertThat(invoice.getAmount()).isEqualByComparingTo("450.00");
		assertThat(invoice.getStatus()).isEqualTo(PlatformInvoiceStatus.ISSUED);
	}

	@Test
	void markPaidIsAOneWayTransition() {
		Organisation organisation = newOrganisation("markpaid-school");
		TenantContext.set(organisation.getId(), null);
		Plan plan = billingService.createPlan("PAID-" + organisation.getId(), "Paid Plan", new BigDecimal("100.00"));
		billingService.subscribe(organisation.getId(), plan.getId(), LocalDate.of(2026, 4, 1));
		PlatformInvoice invoice = billingService.generateInvoice(
				organisation.getId(), LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), LocalDate.of(2026, 4, 15));

		PlatformInvoice paid = billingService.markPaid(invoice.getId());
		assertThat(paid.getStatus()).isEqualTo(PlatformInvoiceStatus.PAID);
		assertThat(paid.getPaidAt()).isNotNull();

		assertThatThrownBy(() -> billingService.markPaid(invoice.getId())).isInstanceOf(IllegalStateException.class);
	}
}
