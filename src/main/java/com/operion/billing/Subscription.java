package com.operion.billing;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.common.BaseEntity;
import com.operion.organisation.Organisation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One row per plan period for an organisation - insert-only history, same convention as
 * TeacherAssignment: changing plans ends this row and BillingService inserts a new one,
 * never mutates planId in place. Not organisation-scoped as a tenant (extends
 * BaseEntity, not TenantScopedEntity) - organisationId is a plain FK here, visible
 * across every org to a platform admin, the opposite of every other module's isolation
 * rule. pricePerStudentPerYear is snapshotted at creation, not read live off Plan - same
 * reasoning as Invoice snapshotting FeeStructure amounts, so a later Plan price change
 * never retroactively alters an org's already-agreed rate.
 */
@Getter
@Entity
@Table(name = "subscriptions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription extends BaseEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "organisation_id")
	private Organisation organisation;

	@ManyToOne(optional = false)
	@JoinColumn(name = "plan_id")
	private Plan plan;

	@Column(name = "price_per_student_per_year", nullable = false)
	private BigDecimal pricePerStudentPerYear;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	/** Nullable - null means ongoing. */
	@Column(name = "end_date")
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SubscriptionStatus status;

	public Subscription(Organisation organisation, Plan plan, LocalDate startDate) {
		this.organisation = organisation;
		this.plan = plan;
		this.pricePerStudentPerYear = plan.getPricePerStudentPerYear();
		this.startDate = startDate;
		this.status = SubscriptionStatus.ACTIVE;
	}

	public void cancel(LocalDate endDate) {
		if (status == SubscriptionStatus.CANCELLED) {
			throw new IllegalStateException("Subscription is already cancelled");
		}
		this.endDate = endDate;
		this.status = SubscriptionStatus.CANCELLED;
	}
}
