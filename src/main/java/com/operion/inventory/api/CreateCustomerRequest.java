package com.operion.inventory.api;

public record CreateCustomerRequest(Long studentId, Long guardianId, String name, String phone) {
}
