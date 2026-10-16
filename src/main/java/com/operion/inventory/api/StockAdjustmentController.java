package com.operion.inventory.api;

import com.operion.common.EnumParam;
import com.operion.common.InvalidRequestException;
import java.util.List;

import com.operion.authorization.RequirePermission;
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
@RequirePermission("INVENTORY_VIEW")
public class StockAdjustmentController {

	private static void requireField(Object value, String field) {
		if (value == null) {
			throw new InvalidRequestException(field + " is required");
		}
	}

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
	@RequirePermission("INVENTORY_ADJUSTMENT_MANAGE")
	public StockAdjustmentResponse record(@RequestBody RecordStockAdjustmentRequest request) {
		requireField(request.itemId(), "itemId");
		requireField(request.campusId(), "campusId");
		requireField(request.adjustmentDate(), "adjustmentDate");
		StockAdjustmentReason reason = EnumParam.parse(StockAdjustmentReason.class, request.reason());
		Item item = findItem(request.itemId());
		Campus campus = findCampus(request.campusId());
		StockAdjustment adjustment = inventoryService.recordAdjustment(item, campus, request.quantityDelta(),
				reason, request.adjustmentDate(), request.remarks());
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
