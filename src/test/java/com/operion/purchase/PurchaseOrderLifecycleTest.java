package com.operion.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.inventory.Item;
import com.operion.inventory.ItemCategory;
import com.operion.inventory.ItemCategoryRepository;
import com.operion.inventory.InventoryService;
import com.operion.inventory.Supplier;
import com.operion.inventory.SupplierRepository;
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
 * Proves the PO status machine (DRAFT -> SUBMITTED -> APPROVED -> PARTIALLY_RECEIVED/
 * RECEIVED, CANCELLED only before receiving starts), that receiving moves stock via
 * InventoryService.recordEntry, and that returns respect what was actually received and
 * move stock back out via InventoryService.recordAdjustment.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, InventoryService.class, PurchaseOrderService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PurchaseOrderLifecycleTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private ItemCategoryRepository itemCategoryRepository;

	@Autowired
	private SupplierRepository supplierRepository;

	@Autowired
	private InventoryService inventoryService;

	@Autowired
	private PurchaseOrderService purchaseOrderService;

	@Autowired
	private PurchaseOrderLineRepository purchaseOrderLineRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(Item item, Campus campus, Supplier supplier) {
	}

	private Fixture setUpFixture(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);

		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		ItemCategory category = itemCategoryRepository.save(new ItemCategory("STATIONERY", "Stationery", null));
		Item item = inventoryService.createItem(category, "ITEM-001", "A4 Paper Ream", "REAM", null, null);
		Supplier supplier = supplierRepository.save(new Supplier("Acme Vendors", null, null, null, null));

		return new Fixture(item, campus, supplier);
	}

	private PurchaseOrder createDraftOrder(Fixture fixture, int quantity) {
		return purchaseOrderService.createOrder(fixture.supplier(), fixture.campus(), LocalDate.of(2026, 2, 1),
				List.of(new PurchaseOrderService.LineInput(fixture.item(), quantity, new BigDecimal("10.00"))));
	}

	private PurchaseOrderLine onlyLineOf(PurchaseOrder order) {
		return purchaseOrderLineRepository.findByPurchaseOrderId(order.getId()).get(0);
	}

	@Test
	void createdOrderStartsInDraftWithItsLine() {
		Fixture fixture = setUpFixture("po-draft");
		PurchaseOrder order = createDraftOrder(fixture, 20);

		assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
		assertThat(purchaseOrderLineRepository.findByPurchaseOrderId(order.getId())).singleElement()
				.satisfies(line -> assertThat(line.getQuantity()).isEqualTo(20));
	}

	@Test
	void cannotApproveBeforeSubmitting() {
		Fixture fixture = setUpFixture("po-approve-before-submit");
		PurchaseOrder order = createDraftOrder(fixture, 20);

		assertThatThrownBy(() -> purchaseOrderService.approve(order)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void submitThenApproveReachesApproved() {
		Fixture fixture = setUpFixture("po-submit-approve");
		PurchaseOrder order = createDraftOrder(fixture, 20);

		purchaseOrderService.submit(order);
		PurchaseOrder approved = purchaseOrderService.approve(order);

		assertThat(approved.getStatus()).isEqualTo(PurchaseOrderStatus.APPROVED);
	}

	@Test
	void cancelIsBlockedOnceReceivingHasStarted() {
		Fixture fixture = setUpFixture("po-cancel-after-receipt");
		PurchaseOrder order = createDraftOrder(fixture, 20);
		purchaseOrderService.submit(order);
		purchaseOrderService.approve(order);
		purchaseOrderService.receiveGoods(order, LocalDate.of(2026, 2, 5),
				List.of(new PurchaseOrderService.ReceiveLineInput(onlyLineOf(order), 5, null)));

		assertThatThrownBy(() -> purchaseOrderService.cancel(order)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void cannotReceiveBeforeApproval() {
		Fixture fixture = setUpFixture("po-receive-before-approval");
		PurchaseOrder order = createDraftOrder(fixture, 20);

		assertThatThrownBy(() -> purchaseOrderService.receiveGoods(order, LocalDate.of(2026, 2, 5),
				List.of(new PurchaseOrderService.ReceiveLineInput(onlyLineOf(order), 5, null))))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void partialReceiptMarksPartiallyReceivedThenFullReceiptMarksReceived() {
		Fixture fixture = setUpFixture("po-partial-receipt");
		PurchaseOrder order = createDraftOrder(fixture, 20);
		purchaseOrderService.submit(order);
		purchaseOrderService.approve(order);

		PurchaseOrder afterFirst = purchaseOrderService.receiveGoods(order, LocalDate.of(2026, 2, 5),
				List.of(new PurchaseOrderService.ReceiveLineInput(onlyLineOf(order), 12, null)));
		assertThat(afterFirst.getStatus()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
		assertThat(inventoryService.getBalance(fixture.item(), fixture.campus())).isEqualTo(12);

		PurchaseOrder afterSecond = purchaseOrderService.receiveGoods(order, LocalDate.of(2026, 2, 10),
				List.of(new PurchaseOrderService.ReceiveLineInput(onlyLineOf(order), 8, null)));
		assertThat(afterSecond.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);
		assertThat(inventoryService.getBalance(fixture.item(), fixture.campus())).isEqualTo(20);
	}

	@Test
	void receivingMoreThanOrderedIsRejected() {
		Fixture fixture = setUpFixture("po-over-receive");
		PurchaseOrder order = createDraftOrder(fixture, 10);
		purchaseOrderService.submit(order);
		purchaseOrderService.approve(order);

		assertThatThrownBy(() -> purchaseOrderService.receiveGoods(order, LocalDate.of(2026, 2, 5),
				List.of(new PurchaseOrderService.ReceiveLineInput(onlyLineOf(order), 11, null))))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void returnDecrementsBalanceAndIsCappedAtReceivedQuantity() {
		Fixture fixture = setUpFixture("po-return");
		PurchaseOrder order = createDraftOrder(fixture, 20);
		purchaseOrderService.submit(order);
		purchaseOrderService.approve(order);
		PurchaseOrderLine line = onlyLineOf(order);
		purchaseOrderService.receiveGoods(order, LocalDate.of(2026, 2, 5), List.of(new PurchaseOrderService.ReceiveLineInput(line, 20, null)));

		purchaseOrderService.recordReturn(line, 5, PurchaseReturnReason.DEFECTIVE, LocalDate.of(2026, 2, 6), "Water damaged");

		assertThat(inventoryService.getBalance(fixture.item(), fixture.campus())).isEqualTo(15);
		assertThatThrownBy(() -> purchaseOrderService.recordReturn(line, 16, PurchaseReturnReason.DEFECTIVE, LocalDate.of(2026, 2, 7), null))
				.isInstanceOf(IllegalStateException.class);
	}
}
