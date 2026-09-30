package com.operion.payroll;

import java.math.BigDecimal;

import com.operion.common.TenantScopedEntity;
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

/** A snapshotted earning or deduction - label and type are copied, not linked to the component. */
@Getter
@Entity
@Table(name = "payslip_lines")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayslipLine extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "payslip_id")
	private Payslip payslip;

	@Column(nullable = false)
	private String label;

	@Enumerated(EnumType.STRING)
	@Column(name = "line_type", nullable = false, length = 20)
	private ComponentType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PayslipLineSource source;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal amount;

	public PayslipLine(Payslip payslip, String label, ComponentType type, PayslipLineSource source, BigDecimal amount) {
		if (label == null || label.isBlank()) {
			throw new IllegalArgumentException("Line label is required");
		}
		if (amount == null || amount.signum() < 0) {
			throw new IllegalArgumentException("Line amount must be zero or more");
		}
		this.payslip = payslip;
		this.label = label.trim();
		this.type = type;
		this.source = source;
		this.amount = amount;
	}
}
