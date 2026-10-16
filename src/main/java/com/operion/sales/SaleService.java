package com.operion.sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.operion.common.InvalidRequestException;
import com.operion.inventory.Customer;
import com.operion.inventory.CustomerStatus;
import com.operion.inventory.InventoryService;
import com.operion.inventory.Item;
import com.operion.inventory.ItemStatus;
import com.operion.organisation.Campus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns sale creation (computing the total, deducting stock via InventoryService.recordIssue)
 * and payment recording. Depends on InventoryService, never the other way - same one-way
 * dependency convention as PurchaseOrderService.
 */
@Service
public class SaleService {

	private final SaleRepository saleRepository;
	private final SaleLineRepository saleLineRepository;
	private final SalePaymentRepository salePaymentRepository;
	private final SaleReceiptCounterRepository saleReceiptCounterRepository;
	private final InventoryService inventoryService;

	public SaleService(SaleRepository saleRepository, SaleLineRepository saleLineRepository, SalePaymentRepository salePaymentRepository,
			SaleReceiptCounterRepository saleReceiptCounterRepository, InventoryService inventoryService) {
		this.saleRepository = saleRepository;
		this.saleLineRepository = saleLineRepository;
		this.salePaymentRepository = salePaymentRepository;
		this.saleReceiptCounterRepository = saleReceiptCounterRepository;
		this.inventoryService = inventoryService;
	}

	public record LineInput(Item item, int quantity, BigDecimal unitPrice) {
	}

	@Transactional
	public Sale createSale(Customer customer, Campus campus, LocalDate saleDate, List<LineInput> lines) {
		if (lines.isEmpty()) {
			throw new InvalidRequestException("A sale must have at least one line item");
		}
		if (customer.getStatus() != CustomerStatus.ACTIVE) {
			throw new IllegalStateException("Customer " + customer.getName() + " is inactive and cannot be sold to");
		}
		for (LineInput line : lines) {
			if (line.item().getStatus() != ItemStatus.ACTIVE) {
				throw new IllegalStateException("Item " + line.item().getName() + " is discontinued and cannot be sold");
			}
		}
		BigDecimal total = lines.stream()
				.map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		Sale sale = saleRepository.save(new Sale(customer, campus, nextReceiptNumber(), saleDate, total));
		for (LineInput line : lines) {
			saleLineRepository.save(new SaleLine(sale, line.item(), line.quantity(), line.unitPrice()));
			inventoryService.recordIssue(line.item(), campus, line.quantity(), saleDate, customer.getName(), "Sale #" + sale.getId(), null);
		}
		return sale;
	}

	@Transactional
	public Sale recordPayment(Long saleId, PaymentMethod method, BigDecimal amount, LocalDate paidAt) {
		if (amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new InvalidRequestException("Payment amount must be positive");
		}
		// Row lock: two payments for the same sale take turns, so each sees what the other already paid.
		Sale sale = saleRepository.findByIdForUpdate(saleId).orElseThrow(() -> new IllegalArgumentException("No sale with id " + saleId));
		sale.applyPayment(amount);
		salePaymentRepository.save(new SalePayment(sale, method, amount, paidAt));
		return saleRepository.save(sale);
	}

	/** Atomic per-organisation sequence - never SELECT MAX()+1. */
	private String nextReceiptNumber() {
		SaleReceiptCounter counter = saleReceiptCounterRepository.findForUpdate()
				.orElseGet(() -> saleReceiptCounterRepository.save(new SaleReceiptCounter()));
		long number = counter.consumeNext();
		saleReceiptCounterRepository.save(counter);
		return "RCT-" + String.format("%06d", number);
	}
}
