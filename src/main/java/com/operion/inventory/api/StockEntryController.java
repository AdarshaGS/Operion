package com.operion.inventory.api;

import com.operion.common.InvalidRequestException;
import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.inventory.InventoryService;
import com.operion.inventory.Item;
import com.operion.inventory.ItemRepository;
import com.operion.inventory.StockEntry;
import com.operion.inventory.StockEntryRepository;
import com.operion.inventory.Supplier;
import com.operion.inventory.SupplierRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/stock-entries")
@RequirePermission("INVENTORY_VIEW")
public class StockEntryController {

	private static void requireField(Object value, String field) {
		if (value == null) {
			throw new InvalidRequestException(field + " is required");
		}
	}

	private final InventoryService inventoryService;
	private final StockEntryRepository stockEntryRepository;
	private final ItemRepository itemRepository;
	private final CampusRepository campusRepository;
	private final SupplierRepository supplierRepository;

	public StockEntryController(InventoryService inventoryService, StockEntryRepository stockEntryRepository,
			ItemRepository itemRepository, CampusRepository campusRepository, SupplierRepository supplierRepository) {
		this.inventoryService = inventoryService;
		this.stockEntryRepository = stockEntryRepository;
		this.itemRepository = itemRepository;
		this.campusRepository = campusRepository;
		this.supplierRepository = supplierRepository;
	}

	@PostMapping
	@RequirePermission("INVENTORY_STOCK_MANAGE")
	public StockEntryResponse record(@RequestBody RecordStockEntryRequest request) {
		requireField(request.itemId(), "itemId");
		requireField(request.campusId(), "campusId");
		requireField(request.entryDate(), "entryDate");
		Item item = findItem(request.itemId());
		Campus campus = findCampus(request.campusId());
		Supplier supplier = request.supplierId() != null ? findSupplier(request.supplierId()) : null;
		StockEntry entry = inventoryService.recordEntry(
				item, campus, request.quantity(), request.unitCost(), request.entryDate(), request.source(), supplier, request.remarks());
		return StockEntryResponse.from(entry);
	}

	@GetMapping
	public List<StockEntryResponse> byItemAndCampus(@RequestParam Long itemId, @RequestParam Long campusId) {
		return stockEntryRepository.findByItemIdAndCampusId(itemId, campusId).stream().map(StockEntryResponse::from).toList();
	}

	private Item findItem(Long id) {
		return itemRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No item with id " + id));
	}

	private Campus findCampus(Long id) {
		return campusRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No campus with id " + id));
	}

	private Supplier findSupplier(Long id) {
		return supplierRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No supplier with id " + id));
	}
}
