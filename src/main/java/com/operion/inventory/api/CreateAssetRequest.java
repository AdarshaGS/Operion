package com.operion.inventory.api;

import java.time.LocalDate;

public record CreateAssetRequest(Long itemId, Long campusId, String assetTag, LocalDate purchaseDate, LocalDate warrantyExpiryDate) {
}
