package com.operion.hr.api;

public record CreateLeaveTypeRequest(String code, String name, Double defaultAnnualDays) {
}
