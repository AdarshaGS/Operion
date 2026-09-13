package com.operion.billing.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.operion.billing.Plan;

public record PlanResponse(Long id, String code, String name, BigDecimal pricePerStudentPerYear, String status,
		Instant createdAt) {

	public static PlanResponse from(Plan plan) {
		return new PlanResponse(plan.getId(), plan.getCode(), plan.getName(), plan.getPricePerStudentPerYear(),
				plan.getStatus().name(), plan.getCreatedAt());
	}
}
