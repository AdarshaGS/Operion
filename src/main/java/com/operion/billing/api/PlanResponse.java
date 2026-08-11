package com.operion.billing.api;

import java.math.BigDecimal;

import com.operion.billing.Plan;

public record PlanResponse(Long id, String code, String name, BigDecimal pricePerStudentPerYear, String status) {

	public static PlanResponse from(Plan plan) {
		return new PlanResponse(plan.getId(), plan.getCode(), plan.getName(), plan.getPricePerStudentPerYear(),
				plan.getStatus().name());
	}
}
