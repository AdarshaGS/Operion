package com.operion.inventory;

import com.operion.common.InvalidRequestException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.operion.identity.Person;
import com.operion.organisation.Campus;
import com.operion.organisation.Department;
import com.operion.student.Student;
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
	private final StockTransferRepository stockTransferRepository;
	private final AssetRepository assetRepository;

	public InventoryService(ItemCategoryRepository itemCategoryRepository, ItemRepository itemRepository,
			StockEntryRepository stockEntryRepository, StockIssueRepository stockIssueRepository,
			StockAdjustmentRepository stockAdjustmentRepository, StockTransferRepository stockTransferRepository,
			AssetRepository assetRepository) {
		this.itemCategoryRepository = itemCategoryRepository;
		this.itemRepository = itemRepository;
		this.stockEntryRepository = stockEntryRepository;
		this.stockIssueRepository = stockIssueRepository;
		this.stockAdjustmentRepository = stockAdjustmentRepository;
		this.stockTransferRepository = stockTransferRepository;
		this.assetRepository = assetRepository;
	}

	public ItemCategory createCategory(String code, String name, String description) {
		requireText(code, "A category code is required");
		requireText(name, "A category name is required");
		return itemCategoryRepository.save(new ItemCategory(code, name, description));
	}

	public Item createItem(ItemCategory category, String code, String name, String unit, String description, Integer reorderLevel) {
		return createItem(category, code, name, unit, description, reorderLevel, null, null);
	}

	public Item createItem(ItemCategory category, String code, String name, String unit, String description, Integer reorderLevel,
			String storageLocation, Supplier defaultSupplier) {
		if (category == null) {
			throw new InvalidRequestException("An item needs a category");
		}
		requireText(code, "An item code is required");
		requireText(name, "An item name is required");
		requireText(unit, "An item unit is required");
		requireReorderLevel(reorderLevel);
		requireActiveSupplier(defaultSupplier);
		Item item = new Item(category, code, name, unit, description);
		item.setReorderLevel(reorderLevel);
		item.setStorageLocation(storageLocation);
		item.setDefaultSupplier(defaultSupplier);
		return itemRepository.save(item);
	}

	public Item discontinueItem(Item item) {
		item.discontinue();
		return itemRepository.save(item);
	}

	public Item updateReorderLevel(Item item, Integer reorderLevel) {
		requireReorderLevel(reorderLevel);
		item.setReorderLevel(reorderLevel);
		return itemRepository.save(item);
	}

	public Item updateStorageLocation(Item item, String storageLocation) {
		item.setStorageLocation(storageLocation);
		return itemRepository.save(item);
	}

	public Item updateDefaultSupplier(Item item, Supplier defaultSupplier) {
		requireActiveSupplier(defaultSupplier);
		item.setDefaultSupplier(defaultSupplier);
		return itemRepository.save(item);
	}

	public int getBalance(Item item, Campus campus) {
		int received = stockEntryRepository.sumQuantityByItemIdAndCampusId(item.getId(), campus.getId());
		int issued = stockIssueRepository.sumQuantityByItemIdAndCampusId(item.getId(), campus.getId());
		int adjusted = stockAdjustmentRepository.sumQuantityDeltaByItemIdAndCampusId(item.getId(), campus.getId());
		return received - issued + adjusted;
	}

	/**
	 * Items with a reorder level set whose computed balance at the campus is at or
	 * below it. Balances for every active item are computed from three grouped-sum
	 * queries (one per stock table) rather than per-item lookups, so this stays a
	 * fixed number of queries regardless of catalog size.
	 */
	public List<LowStockItem> getLowStockItems(Campus campus) {
		Map<Long, Integer> received = sumsByItemId(stockEntryRepository.sumQuantityByCampusIdGroupedByItem(campus.getId()));
		Map<Long, Integer> issued = sumsByItemId(stockIssueRepository.sumQuantityByCampusIdGroupedByItem(campus.getId()));
		Map<Long, Integer> adjusted = sumsByItemId(stockAdjustmentRepository.sumQuantityDeltaByCampusIdGroupedByItem(campus.getId()));

		return itemRepository.findByStatus(ItemStatus.ACTIVE).stream()
				.filter(item -> item.getReorderLevel() != null)
				.map(item -> {
					int balance = received.getOrDefault(item.getId(), 0) - issued.getOrDefault(item.getId(), 0)
							+ adjusted.getOrDefault(item.getId(), 0);
					return new LowStockItem(item, balance);
				})
				.filter(lowStockItem -> lowStockItem.balance() <= lowStockItem.item().getReorderLevel())
				.toList();
	}

	private void lockItem(Item item) {
		itemRepository.findByIdForUpdate(item.getId())
				.orElseThrow(() -> new IllegalArgumentException("No item with id " + item.getId()));
	}

	private static void requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new InvalidRequestException(message);
		}
	}

	private static void requireReorderLevel(Integer reorderLevel) {
		if (reorderLevel != null && reorderLevel < 0) {
			throw new InvalidRequestException("A reorder level cannot be negative");
		}
	}

	private static void requireActiveSupplier(Supplier supplier) {
		if (supplier != null && supplier.getStatus() != SupplierStatus.ACTIVE) {
			throw new IllegalStateException("Supplier " + supplier.getId() + " is inactive");
		}
	}

	private static void requireNotDisposed(Asset asset) {
		if (asset.getStatus() == AssetStatus.DISPOSED) {
			throw new IllegalStateException("Asset " + asset.getId() + " has been disposed of");
		}
	}

	private Map<Long, Integer> sumsByItemId(List<Object[]> rows) {
		Map<Long, Integer> sums = new HashMap<>();
		for (Object[] row : rows) {
			sums.put((Long) row[0], ((Number) row[1]).intValue());
		}
		return sums;
	}

	public StockEntry recordEntry(Item item, Campus campus, int quantity, BigDecimal unitCost, LocalDate entryDate, String source, String remarks) {
		return recordEntry(item, campus, quantity, unitCost, entryDate, source, null, remarks);
	}

	public StockEntry recordEntry(Item item, Campus campus, int quantity, BigDecimal unitCost, LocalDate entryDate, String source,
			Supplier supplier, String remarks) {
		if (item.getStatus() == ItemStatus.DISCONTINUED) {
			throw new IllegalStateException("Item " + item.getId() + " is discontinued and cannot receive new stock");
		}
		if (unitCost != null && unitCost.signum() < 0) {
			throw new InvalidRequestException("A stock entry's unit cost cannot be negative");
		}
		requireActiveSupplier(supplier);
		return stockEntryRepository.save(new StockEntry(item, campus, quantity, unitCost, entryDate, source, supplier, remarks));
	}

	@Transactional
	public StockIssue recordIssue(Item item, Campus campus, int quantity, LocalDate issuedDate, String issuedTo, String purpose, String remarks) {
		return recordIssue(item, campus, quantity, issuedDate, issuedTo, purpose, null, remarks);
	}

	@Transactional
	public StockIssue recordIssue(Item item, Campus campus, int quantity, LocalDate issuedDate, String issuedTo, String purpose,
			BigDecimal unitCost, String remarks) {
		return recordIssue(item, campus, quantity, issuedDate, issuedTo, purpose, unitCost, null, null, null, remarks);
	}

	@Transactional
	public StockIssue recordIssue(Item item, Campus campus, int quantity, LocalDate issuedDate, String issuedTo, String purpose,
			BigDecimal unitCost, Department department, Person recipientPerson, Student recipientStudent, String remarks) {
		if (quantity <= 0) {
			throw new InvalidRequestException("Stock issue quantity must be positive");
		}
		if (unitCost != null && unitCost.signum() < 0) {
			throw new InvalidRequestException("A stock issue's unit cost cannot be negative");
		}
		lockItem(item);
		if (quantity > getBalance(item, campus)) {
			throw new IllegalStateException(
					"Cannot issue " + quantity + " of item " + item.getId() + " at campus " + campus.getId() + " - insufficient stock");
		}
		StockIssue issue = new StockIssue(item, campus, quantity, issuedDate, issuedTo, purpose, unitCost, remarks);
		issue.setDepartment(department);
		issue.setRecipientPerson(recipientPerson);
		issue.setRecipientStudent(recipientStudent);
		return stockIssueRepository.save(issue);
	}

	@Transactional
	public StockAdjustment recordAdjustment(Item item, Campus campus, int quantityDelta, StockAdjustmentReason reason,
			LocalDate adjustmentDate, String remarks) {
		if (reason == null) {
			throw new InvalidRequestException("A stock adjustment needs a reason");
		}
		if (quantityDelta == 0) {
			throw new InvalidRequestException("Stock adjustment quantity delta cannot be zero");
		}
		lockItem(item);
		if (getBalance(item, campus) + quantityDelta < 0) {
			throw new IllegalStateException(
					"Adjustment of " + quantityDelta + " would take item " + item.getId() + " at campus " + campus.getId() + " negative");
		}
		return stockAdjustmentRepository.save(new StockAdjustment(item, campus, quantityDelta, reason, adjustmentDate, remarks));
	}

	/**
	 * Records a transfer as one atomic operation: a TRANSFER_OUT adjustment at
	 * fromCampus (rejected if it would take that campus's balance negative, the same
	 * guard recordAdjustment already enforces), a TRANSFER_IN adjustment at toCampus, and
	 * the StockTransfer audit row itself - same "audit trail entity + StockAdjustment for
	 * the actual balance effect" split as PurchaseReturn (#175).
	 */
	@Transactional
	public StockTransfer recordTransfer(Item item, Campus fromCampus, Campus toCampus, int quantity, LocalDate transferDate, String remarks) {
		if (fromCampus.getId().equals(toCampus.getId())) {
			throw new InvalidRequestException("Cannot transfer stock to the same campus");
		}
		if (quantity <= 0) {
			throw new InvalidRequestException("Stock transfer quantity must be positive");
		}
		String detail = remarks != null ? ": " + remarks : "";
		recordAdjustment(item, fromCampus, -quantity, StockAdjustmentReason.TRANSFER_OUT, transferDate,
				"Transfer to campus " + toCampus.getId() + detail);
		recordAdjustment(item, toCampus, quantity, StockAdjustmentReason.TRANSFER_IN, transferDate,
				"Transfer from campus " + fromCampus.getId() + detail);
		return stockTransferRepository.save(new StockTransfer(item, fromCampus, toCampus, quantity, transferDate, remarks));
	}

	public Asset createAsset(Item item, Campus campus, String assetTag, LocalDate purchaseDate, LocalDate warrantyExpiryDate) {
		requireText(assetTag, "An asset tag is required");
		if (purchaseDate != null && warrantyExpiryDate != null && warrantyExpiryDate.isBefore(purchaseDate)) {
			throw new InvalidRequestException("The warranty cannot end before the purchase date");
		}
		return assetRepository.save(new Asset(item, campus, assetTag, purchaseDate, warrantyExpiryDate));
	}

	public Asset changeAssetStatus(Asset asset, AssetStatus status) {
		requireNotDisposed(asset);
		asset.changeStatus(status);
		return assetRepository.save(asset);
	}

	public Asset assignAsset(Asset asset, Person assignedPerson, String assignedLocation) {
		requireNotDisposed(asset);
		asset.setAssignedPerson(assignedPerson);
		asset.setAssignedLocation(assignedLocation);
		return assetRepository.save(asset);
	}
}
