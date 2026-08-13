package com.operion.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.operion.common.TenantContext;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.student.StudentRepository;
import com.operion.student.StudentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns Plan/Subscription/PlatformInvoice - the platform's own billing of a school, not
 * to be confused with finance.FeeService (a school billing its own students' families).
 * Every method here is reachable only through the platform-admin API surface
 * (PlatformAuthenticationInterceptor), never through a school's own login.
 */
@Service
public class BillingService {

	private final PlanRepository planRepository;
	private final SubscriptionRepository subscriptionRepository;
	private final PlatformInvoiceRepository platformInvoiceRepository;
	private final OrganisationRepository organisationRepository;
	private final StudentRepository studentRepository;

	public BillingService(PlanRepository planRepository, SubscriptionRepository subscriptionRepository,
			PlatformInvoiceRepository platformInvoiceRepository, OrganisationRepository organisationRepository,
			StudentRepository studentRepository) {
		this.planRepository = planRepository;
		this.subscriptionRepository = subscriptionRepository;
		this.platformInvoiceRepository = platformInvoiceRepository;
		this.organisationRepository = organisationRepository;
		this.studentRepository = studentRepository;
	}

	public Plan createPlan(String code, String name, BigDecimal pricePerStudentPerYear) {
		return planRepository.save(new Plan(code, name, pricePerStudentPerYear));
	}

	@Transactional
	public Plan changePlanStatus(Long planId, PlanStatus target) {
		Plan plan = planRepository.findById(planId).orElseThrow(() -> new IllegalArgumentException("No plan with id " + planId));
		plan.changeStatus(target);
		return planRepository.save(plan);
	}

	/**
	 * Ends the org's current ACTIVE subscription (if any) and inserts a new one on the
	 * target plan - insert-only history, never mutates an existing row's plan. One
	 * ACTIVE subscription per org is enforced here, same "one active row" convention as
	 * StudentEnrollment.is_current / the transport module's one-ACTIVE-assignment rule.
	 */
	@Transactional
	public Subscription subscribe(Long organisationId, Long planId, LocalDate startDate) {
		Organisation organisation = organisationRepository.findById(organisationId)
				.orElseThrow(() -> new IllegalArgumentException("No organisation with id " + organisationId));
		Plan plan = planRepository.findById(planId).orElseThrow(() -> new IllegalArgumentException("No plan with id " + planId));

		subscriptionRepository.findByOrganisationIdAndStatus(organisationId, SubscriptionStatus.ACTIVE)
				.ifPresent(current -> {
					current.cancel(startDate);
					subscriptionRepository.save(current);
				});

		return subscriptionRepository.save(new Subscription(organisation, plan, startDate));
	}

	public List<Subscription> subscriptionHistory(Long organisationId) {
		return subscriptionRepository.findByOrganisationIdOrderByStartDateDesc(organisationId);
	}

	/** Cross-org, for the platform dashboard - same "the platform plane is the one place
	 * cross-tenant listing is allowed" precedent as PlatformOrganisationController.list(). */
	public List<Subscription> allSubscriptions() {
		return subscriptionRepository.findAll();
	}

	/**
	 * Snapshots the org's current ACTIVE student headcount and bills it against the
	 * org's ACTIVE subscription's rate for the given period - no date-based proration,
	 * pricePerStudentPerYear is applied as a flat per-period rate (fine for the annual
	 * cycle this is designed around; a shorter/partial period is an edge case out of
	 * scope for this pass).
	 *
	 * Deliberately NOT @Transactional, same reasoning as OrganisationService.provision:
	 * Hibernate resolves the tenant identifier once per session, not per query, so
	 * wrapping this in one transaction would open the session with whatever tenant
	 * TenantContext held on entry (none, for a platform request) and countActiveStudents'
	 * TenantContext.set(organisationId, ...) below would silently do nothing. Each call
	 * here gets its own auto-transaction/session instead - fine, since Organisation and
	 * Subscription aren't tenant-scoped at all and Student's count is the only
	 * tenant-scoped read, isolated to its own short-lived session in countActiveStudents.
	 */
	public PlatformInvoice generateInvoice(Long organisationId, LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate) {
		Organisation organisation = organisationRepository.findById(organisationId)
				.orElseThrow(() -> new IllegalArgumentException("No organisation with id " + organisationId));
		Subscription subscription = subscriptionRepository.findByOrganisationIdAndStatus(organisationId, SubscriptionStatus.ACTIVE)
				.orElseThrow(() -> new IllegalStateException("Organisation " + organisationId + " has no active subscription"));

		int studentCount = countActiveStudents(organisationId);
		BigDecimal amount = subscription.getPricePerStudentPerYear().multiply(BigDecimal.valueOf(studentCount));

		return platformInvoiceRepository.save(
				new PlatformInvoice(organisation, subscription, periodStart, periodEnd, studentCount, amount, dueDate));
	}

	@Transactional
	public PlatformInvoice markPaid(Long invoiceId) {
		PlatformInvoice invoice = platformInvoiceRepository.findById(invoiceId)
				.orElseThrow(() -> new IllegalArgumentException("No platform invoice with id " + invoiceId));
		invoice.markPaid(Instant.now());
		return platformInvoiceRepository.save(invoice);
	}

	public List<PlatformInvoice> invoiceHistory(Long organisationId) {
		return platformInvoiceRepository.findByOrganisationIdOrderByPeriodStartDesc(organisationId);
	}

	/** Cross-org, for the platform dashboard - see allSubscriptions() above. */
	public List<PlatformInvoice> allInvoices() {
		return platformInvoiceRepository.findAll();
	}

	/**
	 * Student is TenantScopedEntity, so counting across a specific org from a platform
	 * request (where TenantContext otherwise carries no organisation) requires
	 * temporarily pointing TenantContext at that one org for the duration of this query -
	 * the same "set before the session opens" requirement flagged for tenant-scoped
	 * lookups everywhere else, just run deliberately cross-tenant here one org at a time.
	 */
	private int countActiveStudents(Long organisationId) {
		Long previousOrganisationId = TenantContext.getOrganisationId();
		Long previousActorId = TenantContext.getActorId();
		try {
			TenantContext.set(organisationId, previousActorId);
			return (int) studentRepository.countByStatus(StudentStatus.ACTIVE);
		} finally {
			TenantContext.set(previousOrganisationId, previousActorId);
		}
	}
}
