package com.operion.sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.operion.inventory.Customer;
import com.operion.inventory.InventoryService;
import com.operion.inventory.Item;
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
			throw new IllegalArgumentException("A sale must have at least one line item");
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
	public SalePayment recordPayment(Sale sale, PaymentMethod method, BigDecimal amount, LocalDate paidAt) {
		if (amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Payment amount must be positive");
		}
		sale.applyPayment(amount);
		saleRepository.save(sale);
		return salePaymentRepository.save(new SalePayment(sale, method, amount, paidAt));
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
