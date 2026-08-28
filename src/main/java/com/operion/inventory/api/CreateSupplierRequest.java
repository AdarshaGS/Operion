package com.operion.inventory.api;

public record CreateSupplierRequest(String name, String contactPerson, String phone, String email, String address) {
}
