package com.operion.inventory.api;

import com.operion.common.InvalidRequestException;
import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.common.imports.ImportRowResult;
import com.operion.inventory.InventoryService;
import com.operion.inventory.Item;
import com.operion.inventory.ItemCategory;
import com.operion.inventory.ItemCategoryRepository;
import com.operion.inventory.ItemImportService;
import com.operion.inventory.ItemRepository;
import com.operion.inventory.ItemStatus;
import com.operion.inventory.Supplier;
import com.operion.inventory.SupplierRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/inventory/items")
@RequirePermission("INVENTORY_VIEW")
public class ItemController {

	private static void requireField(Object value, String field) {
		if (value == null) {
			throw new InvalidRequestException(field + " is required");
		}
	}

	private final InventoryService inventoryService;
	private final ItemRepository itemRepository;
	private final ItemCategoryRepository itemCategoryRepository;
	private final CampusRepository campusRepository;
	private final ItemImportService itemImportService;
	private final SupplierRepository supplierRepository;

	public ItemController(InventoryService inventoryService, ItemRepository itemRepository,
			ItemCategoryRepository itemCategoryRepository, CampusRepository campusRepository, ItemImportService itemImportService,
			SupplierRepository supplierRepository) {
		this.inventoryService = inventoryService;
		this.itemRepository = itemRepository;
		this.itemCategoryRepository = itemCategoryRepository;
		this.campusRepository = campusRepository;
		this.itemImportService = itemImportService;
		this.supplierRepository = supplierRepository;
	}

	@PostMapping
	@RequirePermission("INVENTORY_CATALOG_MANAGE")
	public ItemResponse create(@RequestBody CreateItemRequest request) {
		requireField(request.categoryId(), "categoryId");
		ItemCategory category = itemCategoryRepository.findById(request.categoryId())
				.orElseThrow(() -> new IllegalArgumentException("No item category with id " + request.categoryId()));
		Supplier defaultSupplier = request.defaultSupplierId() != null ? findSupplier(request.defaultSupplierId()) : null;
		Item item = inventoryService.createItem(category, request.code(), request.name(), request.unit(), request.description(),
				request.reorderLevel(), request.storageLocation(), defaultSupplier);
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

	@PostMapping("/{id}/storage-location")
	@RequirePermission("INVENTORY_CATALOG_MANAGE")
	public ItemResponse updateStorageLocation(@PathVariable Long id, @RequestBody UpdateItemStorageLocationRequest request) {
		return ItemResponse.from(inventoryService.updateStorageLocation(findItem(id), request.storageLocation()));
	}

	@PostMapping("/{id}/default-supplier")
	@RequirePermission("INVENTORY_CATALOG_MANAGE")
	public ItemResponse updateDefaultSupplier(@PathVariable Long id, @RequestBody UpdateItemDefaultSupplierRequest request) {
		Supplier defaultSupplier = request.defaultSupplierId() != null ? findSupplier(request.defaultSupplierId()) : null;
		return ItemResponse.from(inventoryService.updateDefaultSupplier(findItem(id), defaultSupplier));
	}

	@GetMapping("/low-stock")
	public List<LowStockItemResponse> lowStock(@RequestParam Long campusId) {
		Campus campus = campusRepository.findById(campusId).orElseThrow(() -> new IllegalArgumentException("No campus with id " + campusId));
		return inventoryService.getLowStockItems(campus).stream().map(LowStockItemResponse::from).toList();
	}

	@GetMapping("/{id}/balance")
	public BalanceResponse balance(@PathVariable Long id, @RequestParam Long campusId) {
		Item item = findItem(id);
		Campus campus = campusRepository.findById(campusId).orElseThrow(() -> new IllegalArgumentException("No campus with id " + campusId));
		return new BalanceResponse(item.getId(), campus.getId(), inventoryService.getBalance(item, campus));
	}

	/** Bulk CSV/Excel import (Imports & exports rebuild), same validate-then-confirm shape
	 * as StudentController's import endpoint - see ItemImportService/ItemRowImportService
	 * for the per-row transaction isolation. */
	@PostMapping("/import")
	@RequirePermission("INVENTORY_CATALOG_MANAGE")
	public List<ImportRowResult> importFile(@RequestParam("file") MultipartFile file,
			@RequestParam(defaultValue = "false") boolean validateOnly) {
		return itemImportService.importFile(file, validateOnly);
	}

	/** Inherits this controller's class-level INVENTORY_VIEW gate. */
	@GetMapping("/export")
	public List<ItemExportResponse> export() {
		return itemRepository.findAll().stream().map(ItemExportResponse::from).toList();
	}

	private Item findItem(Long id) {
		return itemRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No item with id " + id));
	}

	private Supplier findSupplier(Long id) {
		return supplierRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No supplier with id " + id));
	}
}
