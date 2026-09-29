package com.operion.inventory.api;

import java.time.LocalDate;

import com.operion.inventory.Asset;

public record AssetResponse(Long id, Long itemId, String itemName, Long campusId, String assetTag, Long assignedPersonId,
		String assignedPersonName, String assignedLocation, LocalDate purchaseDate, LocalDate warrantyExpiryDate, String status) {

	public static AssetResponse from(Asset asset) {
		return new AssetResponse(asset.getId(), asset.getItem().getId(), asset.getItem().getName(), asset.getCampus().getId(),
				asset.getAssetTag(), asset.getAssignedPerson() != null ? asset.getAssignedPerson().getId() : null,
				asset.getAssignedPerson() != null ? asset.getAssignedPerson().getFirstName() + " " + asset.getAssignedPerson().getLastName()
						: null,
				asset.getAssignedLocation(), asset.getPurchaseDate(), asset.getWarrantyExpiryDate(), asset.getStatus().name());
	}
}
