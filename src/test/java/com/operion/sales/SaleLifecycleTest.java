package com.operion.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.inventory.Customer;
import com.operion.inventory.CustomerRepository;
import com.operion.inventory.Item;
import com.operion.inventory.ItemCategory;
import com.operion.inventory.ItemCategoryRepository;
import com.operion.inventory.InventoryService;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves sale creation deducts stock via InventoryService.recordIssue and rejects an
 * oversell, that totals are computed from line items, that receipt numbers are a
 * sequential per-organisation counter, and that recording payments moves
 * COMPLETED -> PARTIALLY_PAID -> PAID (mirrors Invoice.applyPayment).
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, InventoryService.class, SaleService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SaleLifecycleTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private ItemCategoryRepository itemCategoryRepository;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private InventoryService inventoryService;

	@Autowired
	private SaleService saleService;

	@Autowired
	private SaleLineRepository saleLineRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(Item item, Campus campus, Customer customer) {
	}

	private Fixture setUpFixture(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);

		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		ItemCategory category = itemCategoryRepository.save(new ItemCategory("STATIONERY", "Stationery", null));
		Item item = inventoryService.createItem(category, "ITEM-001", "A4 Paper Ream", "REAM", null, null);
		Customer customer = customerRepository.save(new Customer(null, null, "Walk-in Customer", null));

		return new Fixture(item, campus, customer);
	}

	@Test
	void creatingASaleComputesTotalAndDeductsStock() {
		Fixture fixture = setUpFixture("sale-create");
		inventoryService.recordEntry(fixture.item(), fixture.campus(), 20, new BigDecimal("5.00"), LocalDate.of(2026, 2, 1), "Opening stock", null);

		Sale sale = saleService.createSale(fixture.customer(), fixture.campus(), LocalDate.of(2026, 2, 2),
				List.of(new SaleService.LineInput(fixture.item(), 3, new BigDecimal("10.00"))));

		assertThat(sale.getTotalAmount()).isEqualByComparingTo("30.00");
		assertThat(sale.getReceiptNumber()).isEqualTo("RCT-000001");
		assertThat(sale.getStatus()).isEqualTo(SaleStatus.COMPLETED);
		assertThat(saleLineRepository.findBySaleId(sale.getId())).singleElement()
				.satisfies(line -> assertThat(line.getQuantity()).isEqualTo(3));
		assertThat(inventoryService.getBalance(fixture.item(), fixture.campus())).isEqualTo(17);
	}

	@Test
	void receiptNumbersAreSequentialPerOrganisation() {
		Fixture fixture = setUpFixture("sale-sequence");
		inventoryService.recordEntry(fixture.item(), fixture.campus(), 20, new BigDecimal("5.00"), LocalDate.of(2026, 2, 1), "Opening stock", null);

		Sale first = saleService.createSale(fixture.customer(), fixture.campus(), LocalDate.of(2026, 2, 2),
				List.of(new SaleService.LineInput(fixture.item(), 1, new BigDecimal("10.00"))));
		Sale second = saleService.createSale(fixture.customer(), fixture.campus(), LocalDate.of(2026, 2, 3),
				List.of(new SaleService.LineInput(fixture.item(), 1, new BigDecimal("10.00"))));

		assertThat(first.getReceiptNumber()).isEqualTo("RCT-000001");
		assertThat(second.getReceiptNumber()).isEqualTo("RCT-000002");
	}

	@Test
	void cannotSellMoreThanIsInStock() {
		Fixture fixture = setUpFixture("sale-oversell");
		inventoryService.recordEntry(fixture.item(), fixture.campus(), 5, new BigDecimal("5.00"), LocalDate.of(2026, 2, 1), "Opening stock", null);

		assertThatThrownBy(() -> saleService.createSale(fixture.customer(), fixture.campus(), LocalDate.of(2026, 2, 2),
				List.of(new SaleService.LineInput(fixture.item(), 6, new BigDecimal("10.00")))))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void paymentsAccumulateAndMarkTheSalePaidOnceTheyCoverTheTotal() {
		Fixture fixture = setUpFixture("sale-payment");
		inventoryService.recordEntry(fixture.item(), fixture.campus(), 20, new BigDecimal("5.00"), LocalDate.of(2026, 2, 1), "Opening stock", null);
		Sale sale = saleService.createSale(fixture.customer(), fixture.campus(), LocalDate.of(2026, 2, 2),
				List.of(new SaleService.LineInput(fixture.item(), 2, new BigDecimal("25.00"))));

		saleService.recordPayment(sale, PaymentMethod.CASH, new BigDecimal("20.00"), LocalDate.of(2026, 2, 2));
		assertThat(sale.getStatus()).isEqualTo(SaleStatus.PARTIALLY_PAID);
		assertThat(sale.getOutstanding()).isEqualByComparingTo("30.00");

		saleService.recordPayment(sale, PaymentMethod.UPI, new BigDecimal("30.00"), LocalDate.of(2026, 2, 3));
		assertThat(sale.getStatus()).isEqualTo(SaleStatus.PAID);
		assertThat(sale.getOutstanding()).isEqualByComparingTo("0.00");
	}
}
