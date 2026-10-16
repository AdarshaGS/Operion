package com.operion.purchase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.operion.common.InvalidRequestException;
import com.operion.inventory.InventoryService;
import com.operion.inventory.ItemStatus;
import com.operion.inventory.SupplierStatus;
import com.operion.inventory.Item;
import com.operion.inventory.StockAdjustmentReason;
import com.operion.inventory.Supplier;
import com.operion.organisation.Campus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the PO status machine and the two operations that touch stock: receiving (adds to
 * the item's balance via InventoryService.recordEntry) and returning to a supplier
 * (removes from it via InventoryService.recordAdjustment). Depends on InventoryService,
 * never the other way - com.operion.inventory has no knowledge of Purchase Orders.
 */
@Service
public class PurchaseOrderService {

	private final PurchaseOrderRepository purchaseOrderRepository;
	private final PurchaseOrderLineRepository purchaseOrderLineRepository;
	private final PurchaseReturnRepository purchaseReturnRepository;
	private final InventoryService inventoryService;

	public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository, PurchaseOrderLineRepository purchaseOrderLineRepository,
			PurchaseReturnRepository purchaseReturnRepository, InventoryService inventoryService) {
		this.purchaseOrderRepository = purchaseOrderRepository;
		this.purchaseOrderLineRepository = purchaseOrderLineRepository;
		this.purchaseReturnRepository = purchaseReturnRepository;
		this.inventoryService = inventoryService;
	}

	public record LineInput(Item item, int quantity, BigDecimal unitCost) {
	}

	@Transactional
	public PurchaseOrder createOrder(Supplier supplier, Campus campus, LocalDate expectedDate, List<LineInput> lines) {
		if (lines.isEmpty()) {
			throw new InvalidRequestException("A purchase order must have at least one line item");
		}
		if (supplier.getStatus() != SupplierStatus.ACTIVE) {
			throw new IllegalStateException("Supplier " + supplier.getName() + " is inactive and cannot be ordered from");
		}
		for (LineInput line : lines) {
			if (line.item().getStatus() != ItemStatus.ACTIVE) {
				throw new IllegalStateException("Item " + line.item().getName() + " is discontinued and cannot be ordered");
			}
		}
		PurchaseOrder order = purchaseOrderRepository.save(new PurchaseOrder(supplier, campus, expectedDate));
		for (LineInput line : lines) {
			purchaseOrderLineRepository.save(new PurchaseOrderLine(order, line.item(), line.quantity(), line.unitCost()));
		}
		return order;
	}

	public PurchaseOrder submit(PurchaseOrder order) {
		order.submit();
		return purchaseOrderRepository.save(order);
	}

	public PurchaseOrder approve(PurchaseOrder order) {
		order.approve();
		return purchaseOrderRepository.save(order);
	}

	public PurchaseOrder cancel(PurchaseOrder order) {
		order.cancel();
		return purchaseOrderRepository.save(order);
	}

	public record ReceiveLineInput(PurchaseOrderLine line, int quantity, BigDecimal unitCost) {
	}

	@Transactional
	public PurchaseOrder receiveGoods(PurchaseOrder unlockedOrder, LocalDate entryDate, List<ReceiveLineInput> receivedLines) {
		// Re-read the order under a row lock: the caller's copy was loaded before this transaction, so a receipt
		// that committed in the meantime would not show in its status or in its lines' received quantities.
		PurchaseOrder order = purchaseOrderRepository.findByIdForUpdate(unlockedOrder.getId())
				.orElseThrow(() -> new IllegalArgumentException("No purchase order with id " + unlockedOrder.getId()));
		if (order.getStatus() != PurchaseOrderStatus.APPROVED && order.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
			throw new IllegalStateException("Only an approved purchase order can receive goods, was " + order.getStatus());
		}
		for (ReceiveLineInput received : receivedLines) {
			PurchaseOrderLine line = purchaseOrderLineRepository.findById(received.line().getId())
					.orElseThrow(() -> new IllegalArgumentException("No purchase order line with id " + received.line().getId()));
			if (!line.getPurchaseOrder().getId().equals(order.getId())) {
				throw new IllegalArgumentException("Line " + line.getId() + " does not belong to purchase order " + order.getId());
			}
			line.receive(received.quantity());
			purchaseOrderLineRepository.save(line);
			BigDecimal unitCost = received.unitCost() != null ? received.unitCost() : line.getUnitCost();
			inventoryService.recordEntry(line.getItem(), order.getCampus(), received.quantity(), unitCost, entryDate,
					"Purchase Order #" + order.getId(), null);
		}
		boolean fullyReceived = purchaseOrderLineRepository.findByPurchaseOrderId(order.getId()).stream().allMatch(PurchaseOrderLine::isFullyReceived);
		order.applyReceiptProgress(fullyReceived);
		return purchaseOrderRepository.save(order);
	}

	@Transactional
	public PurchaseReturn recordReturn(PurchaseOrderLine unlockedLine, int quantity, PurchaseReturnReason reason, LocalDate returnDate, String remarks) {
		if (quantity <= 0) {
			throw new InvalidRequestException("Purchase return quantity must be positive");
		}
		// Same row lock as receiving, so concurrent returns cannot both pass the "available to return" check.
		purchaseOrderRepository.findByIdForUpdate(unlockedLine.getPurchaseOrder().getId());
		PurchaseOrderLine line = purchaseOrderLineRepository.findById(unlockedLine.getId()).orElseThrow();
		int alreadyReturned = purchaseReturnRepository.sumQuantityByPurchaseOrderLineId(line.getId());
		int availableToReturn = line.getQuantityReceived() - alreadyReturned;
		if (quantity > availableToReturn) {
			throw new IllegalStateException(
					"Cannot return " + quantity + " of line " + line.getId() + " - only " + availableToReturn + " available to return");
		}
		PurchaseOrder order = line.getPurchaseOrder();
		String adjustmentRemarks = "Return against Purchase Order #" + order.getId() + (remarks != null ? ": " + remarks : "");
		inventoryService.recordAdjustment(line.getItem(), order.getCampus(), -quantity, StockAdjustmentReason.SUPPLIER_RETURN, returnDate,
				adjustmentRemarks);
		return purchaseReturnRepository.save(new PurchaseReturn(line, quantity, reason, returnDate, remarks));
	}
}
