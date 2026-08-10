package com.operion.hr;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Org-scoped lookup (Casual/Sick/Earned), same pattern as FeeCategory. */
@Getter
@Entity
@Table(name = "leave_types")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveType extends TenantScopedEntity {

	@Column(nullable = false)
	private String code;

	@Column(nullable = false)
	private String name;

	/** Nullable - informational default used when allocating a LeaveBalance. */
	@Column(name = "default_annual_days")
	private Double defaultAnnualDays;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LeaveTypeStatus status;

	public LeaveType(String code, String name, Double defaultAnnualDays) {
		this.code = code;
		this.name = name;
		this.defaultAnnualDays = defaultAnnualDays;
		this.status = LeaveTypeStatus.ACTIVE;
	}
}
