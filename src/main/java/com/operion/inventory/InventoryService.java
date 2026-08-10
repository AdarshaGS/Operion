package com.operion.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.organisation.Campus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the catalog and the stock ledger. Balance is computed live from
 * StockEntry/StockIssue/StockAdjustment (SUM entries - SUM issues + SUM adjustment
 * deltas), not stored - per the design sign-off, this module doesn't have Fees'
 * thousands-of-invoices scale justification for a maintained projection column.
 * Issues and adjustments that would take the computed balance negative are rejected.
 */
@Service
public class InventoryService {

	private final ItemCategoryRepository itemCategoryRepository;
	private final ItemRepository itemRepository;
	private final StockEntryRepository stockEntryRepository;
	private final StockIssueRepository stockIssueRepository;
	private final StockAdjustmentRepository stockAdjustmentRepository;

	public InventoryService(ItemCategoryRepository itemCategoryRepository, ItemRepository itemRepository,
			StockEntryRepository stockEntryRepository, StockIssueRepository stockIssueRepository,
			StockAdjustmentRepository stockAdjustmentRepository) {
		this.itemCategoryRepository = itemCategoryRepository;
		this.itemRepository = itemRepository;
		this.stockEntryRepository = stockEntryRepository;
		this.stockIssueRepository = stockIssueRepository;
		this.stockAdjustmentRepository = stockAdjustmentRepository;
	}

	public ItemCategory createCategory(String code, String name, String description) {
		return itemCategoryRepository.save(new ItemCategory(code, name, description));
	}

	public Item createItem(ItemCategory category, String code, String name, String unit, String description) {
		return itemRepository.save(new Item(category, code, name, unit, description));
	}

	public Item discontinueItem(Item item) {
		item.discontinue();
		return itemRepository.save(item);
	}

	public int getBalance(Item item, Campus campus) {
		int received = stockEntryRepository.sumQuantityByItemIdAndCampusId(item.getId(), campus.getId());
		int issued = stockIssueRepository.sumQuantityByItemIdAndCampusId(item.getId(), campus.getId());
		int adjusted = stockAdjustmentRepository.sumQuantityDeltaByItemIdAndCampusId(item.getId(), campus.getId());
		return received - issued + adjusted;
	}

	public StockEntry recordEntry(Item item, Campus campus, int quantity, BigDecimal unitCost, LocalDate entryDate, String source, String remarks) {
		return stockEntryRepository.save(new StockEntry(item, campus, quantity, unitCost, entryDate, source, remarks));
	}

	@Transactional
	public StockIssue recordIssue(Item item, Campus campus, int quantity, LocalDate issuedDate, String issuedTo, String purpose, String remarks) {
		if (quantity > getBalance(item, campus)) {
			throw new IllegalStateException(
					"Cannot issue " + quantity + " of item " + item.getId() + " at campus " + campus.getId() + " - insufficient stock");
		}
		return stockIssueRepository.save(new StockIssue(item, campus, quantity, issuedDate, issuedTo, purpose, remarks));
	}

	@Transactional
	public StockAdjustment recordAdjustment(Item item, Campus campus, int quantityDelta, StockAdjustmentReason reason,
			LocalDate adjustmentDate, String remarks) {
		if (getBalance(item, campus) + quantityDelta < 0) {
			throw new IllegalStateException(
					"Adjustment of " + quantityDelta + " would take item " + item.getId() + " at campus " + campus.getId() + " negative");
		}
		return stockAdjustmentRepository.save(new StockAdjustment(item, campus, quantityDelta, reason, adjustmentDate, remarks));
	}
}
