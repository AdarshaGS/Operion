package com.operion.inventory.api;

import java.util.List;

import com.operion.inventory.InventoryService;
import com.operion.inventory.ItemCategoryRepository;
import com.operion.inventory.ItemCategoryStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/categories")
public class ItemCategoryController {

	private final InventoryService inventoryService;
	private final ItemCategoryRepository itemCategoryRepository;

	public ItemCategoryController(InventoryService inventoryService, ItemCategoryRepository itemCategoryRepository) {
		this.inventoryService = inventoryService;
		this.itemCategoryRepository = itemCategoryRepository;
	}

	@PostMapping
	public ItemCategoryResponse create(@RequestBody CreateItemCategoryRequest request) {
		return ItemCategoryResponse.from(inventoryService.createCategory(request.code(), request.name(), request.description()));
	}

	@GetMapping
	public List<ItemCategoryResponse> list() {
		return itemCategoryRepository.findByStatus(ItemCategoryStatus.ACTIVE).stream().map(ItemCategoryResponse::from).toList();
	}
}
