package com.operion.inventory.api;

import java.util.List;

import com.operion.inventory.InventoryService;
import com.operion.inventory.Item;
import com.operion.inventory.ItemRepository;
import com.operion.inventory.StockEntry;
import com.operion.inventory.StockEntryRepository;
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
public class StockEntryController {

	private final InventoryService inventoryService;
	private final StockEntryRepository stockEntryRepository;
	private final ItemRepository itemRepository;
	private final CampusRepository campusRepository;

	public StockEntryController(InventoryService inventoryService, StockEntryRepository stockEntryRepository,
			ItemRepository itemRepository, CampusRepository campusRepository) {
		this.inventoryService = inventoryService;
		this.stockEntryRepository = stockEntryRepository;
		this.itemRepository = itemRepository;
		this.campusRepository = campusRepository;
	}

	@PostMapping
	public StockEntryResponse record(@RequestBody RecordStockEntryRequest request) {
		Item item = findItem(request.itemId());
		Campus campus = findCampus(request.campusId());
		StockEntry entry = inventoryService.recordEntry(
				item, campus, request.quantity(), request.unitCost(), request.entryDate(), request.source(), request.remarks());
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
}
