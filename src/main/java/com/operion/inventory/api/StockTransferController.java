package com.operion.inventory.api;

import com.operion.common.InvalidRequestException;
import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.inventory.InventoryService;
import com.operion.inventory.Item;
import com.operion.inventory.ItemRepository;
import com.operion.inventory.StockTransfer;
import com.operion.inventory.StockTransferRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/stock-transfers")
@RequirePermission("INVENTORY_VIEW")
public class StockTransferController {

	private static void requireField(Object value, String field) {
		if (value == null) {
			throw new InvalidRequestException(field + " is required");
		}
	}

	private final InventoryService inventoryService;
	private final StockTransferRepository stockTransferRepository;
	private final ItemRepository itemRepository;
	private final CampusRepository campusRepository;

	public StockTransferController(InventoryService inventoryService, StockTransferRepository stockTransferRepository,
			ItemRepository itemRepository, CampusRepository campusRepository) {
		this.inventoryService = inventoryService;
		this.stockTransferRepository = stockTransferRepository;
		this.itemRepository = itemRepository;
		this.campusRepository = campusRepository;
	}

	@PostMapping
	@RequirePermission("INVENTORY_STOCK_MANAGE")
	public StockTransferResponse record(@RequestBody RecordStockTransferRequest request) {
		requireField(request.itemId(), "itemId");
		requireField(request.fromCampusId(), "fromCampusId");
		requireField(request.toCampusId(), "toCampusId");
		requireField(request.transferDate(), "transferDate");
		Item item = findItem(request.itemId());
		Campus fromCampus = findCampus(request.fromCampusId());
		Campus toCampus = findCampus(request.toCampusId());
		StockTransfer transfer = inventoryService.recordTransfer(item, fromCampus, toCampus, request.quantity(), request.transferDate(),
				request.remarks());
		return StockTransferResponse.from(transfer);
	}

	/** Transfers where the given campus is either the source or the destination - so a
	 * campus's stock ledger shows both outgoing and incoming transfers. */
	@GetMapping
	public List<StockTransferResponse> byItemAndCampus(@RequestParam Long itemId, @RequestParam Long campusId) {
		return stockTransferRepository.findByItemIdAndEitherCampusId(itemId, campusId).stream().map(StockTransferResponse::from).toList();
	}

	private Item findItem(Long id) {
		return itemRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No item with id " + id));
	}

	private Campus findCampus(Long id) {
		return campusRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No campus with id " + id));
	}
}
