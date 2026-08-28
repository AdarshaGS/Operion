package com.operion.inventory.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.inventory.InventoryService;
import com.operion.inventory.Item;
import com.operion.inventory.ItemCategory;
import com.operion.inventory.ItemCategoryRepository;
import com.operion.inventory.ItemRepository;
import com.operion.inventory.ItemStatus;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/items")
@RequirePermission("INVENTORY_VIEW")
public class ItemController {

	private final InventoryService inventoryService;
	private final ItemRepository itemRepository;
	private final ItemCategoryRepository itemCategoryRepository;
	private final CampusRepository campusRepository;

	public ItemController(InventoryService inventoryService, ItemRepository itemRepository,
			ItemCategoryRepository itemCategoryRepository, CampusRepository campusRepository) {
		this.inventoryService = inventoryService;
		this.itemRepository = itemRepository;
		this.itemCategoryRepository = itemCategoryRepository;
		this.campusRepository = campusRepository;
	}

	@PostMapping
	@RequirePermission("INVENTORY_CATALOG_MANAGE")
	public ItemResponse create(@RequestBody CreateItemRequest request) {
		ItemCategory category = itemCategoryRepository.findById(request.categoryId())
				.orElseThrow(() -> new IllegalArgumentException("No item category with id " + request.categoryId()));
		Item item = inventoryService.createItem(category, request.code(), request.name(), request.unit(), request.description(),
				request.reorderLevel());
		return ItemResponse.from(item);
	}

	@GetMapping
	public List<ItemResponse> list() {
		return itemRepository.findByStatus(ItemStatus.ACTIVE).stream().map(ItemResponse::from).toList();
	}

	@PostMapping("/{id}/discontinue")
	@RequirePermission("INVENTORY_CATALOG_MANAGE")
	public ItemResponse discontinue(@PathVariable Long id) {
		return ItemResponse.from(inventoryService.discontinueItem(findItem(id)));
	}

	@PostMapping("/{id}/reorder-level")
	@RequirePermission("INVENTORY_CATALOG_MANAGE")
	public ItemResponse updateReorderLevel(@PathVariable Long id, @RequestBody UpdateItemReorderLevelRequest request) {
		return ItemResponse.from(inventoryService.updateReorderLevel(findItem(id), request.reorderLevel()));
	}

	@GetMapping("/{id}/balance")
	public BalanceResponse balance(@PathVariable Long id, @RequestParam Long campusId) {
		Item item = findItem(id);
		Campus campus = campusRepository.findById(campusId).orElseThrow(() -> new IllegalArgumentException("No campus with id " + campusId));
		return new BalanceResponse(item.getId(), campus.getId(), inventoryService.getBalance(item, campus));
	}

	private Item findItem(Long id) {
		return itemRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No item with id " + id));
	}
}
