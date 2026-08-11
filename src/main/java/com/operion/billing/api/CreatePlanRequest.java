package com.operion.billing.api;

import java.math.BigDecimal;

public record CreatePlanRequest(String code, String name, BigDecimal pricePerStudentPerYear) {
}
