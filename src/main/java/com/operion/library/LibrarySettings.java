package com.operion.library;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Org-wide borrowing policy: max concurrent loans and default borrowing period, each
 * configurable per borrower type (student vs. staff, per #169), and whether issuing to
 * a borrower with an overdue book is blocked (#170, opt-in - defaults to off so an
 * existing org's borrow flow doesn't suddenly start rejecting issues). Lazy, at most
 * one row per organisation - same "no row until first PUT, defaults on GET" convention
 * as ExaminationSettings.
 */
@Getter
@Setter
@Entity
@Table(name = "library_settings")
public class LibrarySettings extends TenantScopedEntity {

	public static final int DEFAULT_MAX_LOANS_STUDENT = 3;
	public static final int DEFAULT_MAX_LOANS_STAFF = 5;
	public static final int DEFAULT_BORROWING_PERIOD_DAYS_STUDENT = 14;
	public static final int DEFAULT_BORROWING_PERIOD_DAYS_STAFF = 30;
	public static final boolean DEFAULT_BLOCK_ISSUE_ON_OVERDUE = false;

	@Column(name = "max_loans_student", nullable = false)
	private int maxLoansStudent;

	@Column(name = "max_loans_staff", nullable = false)
	private int maxLoansStaff;

	@Column(name = "borrowing_period_days_student", nullable = false)
	private int borrowingPeriodDaysStudent;

	@Column(name = "borrowing_period_days_staff", nullable = false)
	private int borrowingPeriodDaysStaff;

	@Column(name = "block_issue_on_overdue", nullable = false)
	private boolean blockIssueOnOverdue;

	public LibrarySettings() {
		this.maxLoansStudent = DEFAULT_MAX_LOANS_STUDENT;
		this.maxLoansStaff = DEFAULT_MAX_LOANS_STAFF;
		this.borrowingPeriodDaysStudent = DEFAULT_BORROWING_PERIOD_DAYS_STUDENT;
		this.borrowingPeriodDaysStaff = DEFAULT_BORROWING_PERIOD_DAYS_STAFF;
		this.blockIssueOnOverdue = DEFAULT_BLOCK_ISSUE_ON_OVERDUE;
	}

	public int maxLoansFor(boolean staff) {
		return staff ? maxLoansStaff : maxLoansStudent;
	}

	public int borrowingPeriodDaysFor(boolean staff) {
		return staff ? borrowingPeriodDaysStaff : borrowingPeriodDaysStudent;
	}
}
