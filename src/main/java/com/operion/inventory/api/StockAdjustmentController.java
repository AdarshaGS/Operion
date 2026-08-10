package com.operion.inventory.api;

import java.util.List;

import com.operion.inventory.InventoryService;
import com.operion.inventory.Item;
import com.operion.inventory.ItemRepository;
import com.operion.inventory.StockAdjustment;
import com.operion.inventory.StockAdjustmentReason;
import com.operion.inventory.StockAdjustmentRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/stock-adjustments")
public class StockAdjustmentController {

	private final InventoryService inventoryService;
	private final StockAdjustmentRepository stockAdjustmentRepository;
	private final ItemRepository itemRepository;
	private final CampusRepository campusRepository;

	public StockAdjustmentController(InventoryService inventoryService, StockAdjustmentRepository stockAdjustmentRepository,
			ItemRepository itemRepository, CampusRepository campusRepository) {
		this.inventoryService = inventoryService;
		this.stockAdjustmentRepository = stockAdjustmentRepository;
		this.itemRepository = itemRepository;
		this.campusRepository = campusRepository;
	}

	@PostMapping
	public StockAdjustmentResponse record(@RequestBody RecordStockAdjustmentRequest request) {
		Item item = findItem(request.itemId());
		Campus campus = findCampus(request.campusId());
		StockAdjustment adjustment = inventoryService.recordAdjustment(item, campus, request.quantityDelta(),
				StockAdjustmentReason.valueOf(request.reason()), request.adjustmentDate(), request.remarks());
		return StockAdjustmentResponse.from(adjustment);
	}

	@GetMapping
	public List<StockAdjustmentResponse> byItemAndCampus(@RequestParam Long itemId, @RequestParam Long campusId) {
		return stockAdjustmentRepository.findByItemIdAndCampusId(itemId, campusId).stream().map(StockAdjustmentResponse::from).toList();
	}

	private Item findItem(Long id) {
		return itemRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No item with id " + id));
	}

	private Campus findCampus(Long id) {
		return campusRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No campus with id " + id));
	}
}
