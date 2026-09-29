package com.operion.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
import com.operion.identity.Person;
import com.operion.organisation.Campus;
import com.operion.organisation.Department;
import com.operion.student.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A stock issuance - campus-scoped, insert-only. issuedTo stays required free text
 * ("Class 5B", "Science Lab") for the common non-individual case; department/
 * recipientPerson (staff)/recipientStudent are optional structured links, at most one
 * set (#174 - enforced in StockIssueController, the same "no dedicated service" shape
 * as Customer's mutually-exclusive student/guardian), for when the recipient actually
 * is a trackable department/staff member/student. InventoryService blocks an issue that
 * would take the computed balance negative.
 */
@Getter
@Entity
@Table(name = "stock_issues")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockIssue extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "item_id")
	private Item item;

	@ManyToOne(optional = false)
	@JoinColumn(name = "campus_id")
	private Campus campus;

	@Column(nullable = false)
	private int quantity;

	@Column(name = "issued_date", nullable = false)
	private LocalDate issuedDate;

	@Column(name = "issued_to", nullable = false)
	private String issuedTo;

	/** Nullable. */
	private String purpose;

	/** Nullable - captured when known, same "issue cost" gap #173 closes for the entry
	 * side's unitCost. */
	@Column(name = "unit_cost", precision = 10, scale = 2)
	private BigDecimal unitCost;

	/** Nullable - set only when this issue's recipient is a specific department. */
	@Setter
	@ManyToOne
	@JoinColumn(name = "department_id")
	private Department department;

	/** Nullable - set only when this issue's recipient is a specific staff member. */
	@Setter
	@ManyToOne
	@JoinColumn(name = "recipient_person_id")
	private Person recipientPerson;

	/** Nullable - set only when this issue's recipient is a specific student. */
	@Setter
	@ManyToOne
	@JoinColumn(name = "recipient_student_id")
	private Student recipientStudent;

	/** Nullable. */
	private String remarks;

	public StockIssue(Item item, Campus campus, int quantity, LocalDate issuedDate, String issuedTo, String purpose, String remarks) {
		this(item, campus, quantity, issuedDate, issuedTo, purpose, null, remarks);
	}

	public StockIssue(Item item, Campus campus, int quantity, LocalDate issuedDate, String issuedTo, String purpose, BigDecimal unitCost,
			String remarks) {
		if (quantity <= 0) {
			throw new IllegalArgumentException("Stock issue quantity must be positive");
		}
		this.item = item;
		this.campus = campus;
		this.quantity = quantity;
		this.issuedDate = issuedDate;
		this.issuedTo = issuedTo;
		this.purpose = purpose;
		this.unitCost = unitCost;
		this.remarks = remarks;
	}
}
