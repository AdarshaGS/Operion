package com.operion.inventory;

import java.util.Map;

import com.operion.common.imports.ImportRowResult;
import com.operion.common.imports.ImportRowStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * One row, one transaction - same REQUIRES_NEW isolation as StudentRowImportService, for
 * the same reason (a bad row shouldn't poison rows already committed earlier in the
 * batch). Throws on any row-level failure rather than catching internally; ItemImportService
 * is what converts the exception into an ImportRowResult.
 */
@Service
public class ItemRowImportService {

	private final ItemCategoryRepository itemCategoryRepository;
	private final ItemRepository itemRepository;
	private final InventoryService inventoryService;

	public ItemRowImportService(ItemCategoryRepository itemCategoryRepository, ItemRepository itemRepository,
			InventoryService inventoryService) {
		this.itemCategoryRepository = itemCategoryRepository;
		this.itemRepository = itemRepository;
		this.inventoryService = inventoryService;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ImportRowResult importRow(int rowNumber, Map<String, String> row, boolean validateOnly) {
		String code = require(row, "code");
		String name = require(row, "name");
		String unit = require(row, "unit");
		String categoryName = require(row, "category");

		ItemCategory category = itemCategoryRepository.findByNameIgnoreCase(categoryName)
				.orElseThrow(() -> new IllegalArgumentException("No item category named '" + categoryName + "'"));

		var existing = itemRepository.findByCodeIgnoreCase(code);
		if (existing.isPresent()) {
			return new ImportRowResult(rowNumber, ImportRowStatus.DUPLICATE, "Item with code '" + code + "' already exists",
					existing.get().getId());
		}

		if (validateOnly) {
			return new ImportRowResult(rowNumber, ImportRowStatus.VALID, "Valid", null);
		}

		Item item = inventoryService.createItem(category, code, name, unit, blankToNull(row.get("description")),
				parseInt(row.get("reorderLevel")));
		return new ImportRowResult(rowNumber, ImportRowStatus.IMPORTED, "Created", item.getId());
	}

	private static String require(Map<String, String> row, String field) {
		String value = blankToNull(row.get(field));
		if (value == null) {
			throw new IllegalArgumentException(field + " is required");
		}
		return value;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static Integer parseInt(String value) {
		String trimmed = blankToNull(value);
		return trimmed == null ? null : Integer.parseInt(trimmed);
	}
}
