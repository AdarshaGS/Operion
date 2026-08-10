package com.operion.inventory.api;

public record BalanceResponse(Long itemId, Long campusId, int balance) {
}
