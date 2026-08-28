package com.operion.inventory.api;

public record CreateItemRequest(Long categoryId, String code, String name, String unit, String description, Integer reorderLevel) {
}
