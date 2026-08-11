package com.operion.billing;

import java.math.BigDecimal;

import com.operion.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The platform's own pricing catalog - not organisation-scoped (extends BaseEntity, not
 * TenantScopedEntity), since it's global across every tenant, same catalog-vs-instance
 * split as Book/Item. Platform-admin managed only.
 */
@Getter
@Entity
@Table(name = "plans")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Plan extends BaseEntity {

	@Column(nullable = false, unique = true)
	private String code;

	@Column(nullable = false)
	private String name;

	@Column(name = "price_per_student_per_year", nullable = false)
	private BigDecimal pricePerStudentPerYear;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PlanStatus status;

	public Plan(String code, String name, BigDecimal pricePerStudentPerYear) {
		this.code = code;
		this.name = name;
		this.pricePerStudentPerYear = pricePerStudentPerYear;
		this.status = PlanStatus.ACTIVE;
	}

	public void changeStatus(PlanStatus target) {
		this.status = target;
	}
}
