package com.operion.inventory.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.inventory.InventoryService;
import com.operion.inventory.Item;
import com.operion.inventory.ItemRepository;
import com.operion.inventory.StockIssue;
import com.operion.inventory.StockIssueRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/stock-issues")
@RequirePermission("INVENTORY_VIEW")
public class StockIssueController {

	private final InventoryService inventoryService;
	private final StockIssueRepository stockIssueRepository;
	private final ItemRepository itemRepository;
	private final CampusRepository campusRepository;

	public StockIssueController(InventoryService inventoryService, StockIssueRepository stockIssueRepository,
			ItemRepository itemRepository, CampusRepository campusRepository) {
		this.inventoryService = inventoryService;
		this.stockIssueRepository = stockIssueRepository;
		this.itemRepository = itemRepository;
		this.campusRepository = campusRepository;
	}

	@PostMapping
	@RequirePermission("INVENTORY_STOCK_MANAGE")
	public StockIssueResponse record(@RequestBody RecordStockIssueRequest request) {
		Item item = findItem(request.itemId());
		Campus campus = findCampus(request.campusId());
		StockIssue issue = inventoryService.recordIssue(
				item, campus, request.quantity(), request.issuedDate(), request.issuedTo(), request.purpose(), request.remarks());
		return StockIssueResponse.from(issue);
	}

	@GetMapping
	public List<StockIssueResponse> byItemAndCampus(@RequestParam Long itemId, @RequestParam Long campusId) {
		return stockIssueRepository.findByItemIdAndCampusId(itemId, campusId).stream().map(StockIssueResponse::from).toList();
	}

	private Item findItem(Long id) {
		return itemRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No item with id " + id));
	}

	private Campus findCampus(Long id) {
		return campusRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No campus with id " + id));
	}
}
