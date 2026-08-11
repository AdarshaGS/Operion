package com.operion.billing.api;

import java.time.LocalDate;

public record CreateSubscriptionRequest(Long planId, LocalDate startDate) {
}
