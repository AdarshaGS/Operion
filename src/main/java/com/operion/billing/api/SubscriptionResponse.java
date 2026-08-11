package com.operion.billing.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.billing.Subscription;

public record SubscriptionResponse(
		Long id, Long organisationId, Long planId, BigDecimal pricePerStudentPerYear, LocalDate startDate, LocalDate endDate, String status) {

	public static SubscriptionResponse from(Subscription subscription) {
		return new SubscriptionResponse(
				subscription.getId(),
				subscription.getOrganisation().getId(),
				subscription.getPlan().getId(),
				subscription.getPricePerStudentPerYear(),
				subscription.getStartDate(),
				subscription.getEndDate(),
				subscription.getStatus().name());
	}
}
