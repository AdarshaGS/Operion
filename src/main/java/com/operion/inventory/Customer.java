package com.operion.inventory;

import com.operion.common.TenantScopedEntity;
import com.operion.parent.Guardian;
import com.operion.student.Student;
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
 * Store-sales master data for GitHub #52, laid down ahead of the Sales module (Milestone
 * 7) that will actually consume it - kept in com.operion.inventory per that ticket's own
 * suggested placement, since no com.operion.sales package exists yet to decide alongside.
 * At most one of student/guardian is ever set (enforced in CustomerController, the same
 * "no dedicated service" shape as Supplier/Department) - link one for purchase-history
 * continuity, or leave both null for a walk-in with no account. name/phone are always
 * captured directly rather than derived through the link, so any consumer (a picker, a
 * receipt) can render a customer without joining through Person.
 */
@Getter
@Entity
@Table(name = "customers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer extends TenantScopedEntity {

	/** Nullable - set only when this customer is an existing Student. */
	@ManyToOne
	@JoinColumn(name = "student_id")
	private Student student;

	/** Nullable - set only when this customer is an existing Guardian. */
	@ManyToOne
	@JoinColumn(name = "guardian_id")
	private Guardian guardian;

	@Column(nullable = false)
	private String name;

	private String phone;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CustomerStatus status;

	public Customer(Student student, Guardian guardian, String name, String phone) {
		this.student = student;
		this.guardian = guardian;
		this.name = name;
		this.phone = phone;
		this.status = CustomerStatus.ACTIVE;
	}

	public void changeStatus(CustomerStatus status) {
		this.status = status;
	}
}
