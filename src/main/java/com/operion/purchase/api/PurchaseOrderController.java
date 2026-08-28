package com.operion.purchase.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.inventory.Item;
import com.operion.inventory.ItemRepository;
import com.operion.inventory.Supplier;
import com.operion.inventory.SupplierRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.purchase.PurchaseOrder;
import com.operion.purchase.PurchaseOrderLine;
import com.operion.purchase.PurchaseOrderLineRepository;
import com.operion.purchase.PurchaseOrderRepository;
import com.operion.purchase.PurchaseOrderService;
import com.operion.purchase.PurchaseReturnReason;
import com.operion.purchase.PurchaseReturnRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/purchase/orders")
@RequirePermission("PURCHASE_VIEW")
public class PurchaseOrderController {

	private final PurchaseOrderService purchaseOrderService;
	private final PurchaseOrderRepository purchaseOrderRepository;
	private final PurchaseOrderLineRepository purchaseOrderLineRepository;
	private final PurchaseReturnRepository purchaseReturnRepository;
	private final SupplierRepository supplierRepository;
	private final CampusRepository campusRepository;
	private final ItemRepository itemRepository;

	public PurchaseOrderController(PurchaseOrderService purchaseOrderService, PurchaseOrderRepository purchaseOrderRepository,
			PurchaseOrderLineRepository purchaseOrderLineRepository, PurchaseReturnRepository purchaseReturnRepository,
			SupplierRepository supplierRepository, CampusRepository campusRepository, ItemRepository itemRepository) {
		this.purchaseOrderService = purchaseOrderService;
		this.purchaseOrderRepository = purchaseOrderRepository;
		this.purchaseOrderLineRepository = purchaseOrderLineRepository;
		this.purchaseReturnRepository = purchaseReturnRepository;
		this.supplierRepository = supplierRepository;
		this.campusRepository = campusRepository;
		this.itemRepository = itemRepository;
	}

	@PostMapping
	@RequirePermission("PURCHASE_MANAGE")
	public PurchaseOrderResponse create(@RequestBody CreatePurchaseOrderRequest request) {
		Supplier supplier = findSupplier(request.supplierId());
		Campus campus = findCampus(request.campusId());
		List<PurchaseOrderService.LineInput> lines = request.lines().stream()
				.map(line -> new PurchaseOrderService.LineInput(findItem(line.itemId()), line.quantity(), line.unitCost()))
				.toList();
		PurchaseOrder order = purchaseOrderService.createOrder(supplier, campus, request.expectedDate(), lines);
		return PurchaseOrderResponse.from(order);
	}

	@GetMapping
	public List<PurchaseOrderResponse> list() {
		return purchaseOrderRepository.findAll().stream().map(PurchaseOrderResponse::from).toList();
	}

	@GetMapping("/{id}")
	public PurchaseOrderDetailResponse detail(@PathVariable Long id) {
		return toDetailResponse(findOrder(id));
	}

	@PostMapping("/{id}/submit")
	@RequirePermission("PURCHASE_MANAGE")
	public PurchaseOrderResponse submit(@PathVariable Long id) {
		return PurchaseOrderResponse.from(purchaseOrderService.submit(findOrder(id)));
	}

	@PostMapping("/{id}/approve")
	@RequirePermission("PURCHASE_MANAGE")
	public PurchaseOrderResponse approve(@PathVariable Long id) {
		return PurchaseOrderResponse.from(purchaseOrderService.approve(findOrder(id)));
	}

	@PostMapping("/{id}/cancel")
	@RequirePermission("PURCHASE_MANAGE")
	public PurchaseOrderResponse cancel(@PathVariable Long id) {
		return PurchaseOrderResponse.from(purchaseOrderService.cancel(findOrder(id)));
	}

	@PostMapping("/{id}/receive")
	@RequirePermission("PURCHASE_MANAGE")
	public PurchaseOrderDetailResponse receive(@PathVariable Long id, @RequestBody ReceiveGoodsRequest request) {
		PurchaseOrder order = findOrder(id);
		List<PurchaseOrderService.ReceiveLineInput> receivedLines = request.lines().stream()
				.map(line -> new PurchaseOrderService.ReceiveLineInput(findLine(line.lineId()), line.quantity(), line.unitCost()))
				.toList();
		PurchaseOrder received = purchaseOrderService.receiveGoods(order, request.entryDate(), receivedLines);
		return toDetailResponse(received);
	}

	@PostMapping("/{orderId}/lines/{lineId}/returns")
	@RequirePermission("PURCHASE_MANAGE")
	public PurchaseReturnResponse recordReturn(@PathVariable Long orderId, @PathVariable Long lineId, @RequestBody RecordPurchaseReturnRequest request) {
		PurchaseOrderLine line = findLine(lineId);
		if (!line.getPurchaseOrder().getId().equals(orderId)) {
			throw new IllegalArgumentException("Line " + lineId + " does not belong to purchase order " + orderId);
		}
		var purchaseReturn = purchaseOrderService.recordReturn(line, request.quantity(), PurchaseReturnReason.valueOf(request.reason()),
				request.returnDate(), request.remarks());
		return PurchaseReturnResponse.from(purchaseReturn);
	}

	private PurchaseOrderDetailResponse toDetailResponse(PurchaseOrder order) {
		List<PurchaseOrderLineResponse> lines = purchaseOrderLineRepository.findByPurchaseOrderId(order.getId()).stream()
				.map(line -> PurchaseOrderLineResponse.from(line, purchaseReturnRepository.sumQuantityByPurchaseOrderLineId(line.getId())))
				.toList();
		return PurchaseOrderDetailResponse.from(order, lines);
	}

	private PurchaseOrder findOrder(Long id) {
		return purchaseOrderRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No purchase order with id " + id));
	}

	private PurchaseOrderLine findLine(Long id) {
		return purchaseOrderLineRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No purchase order line with id " + id));
	}

	private Supplier findSupplier(Long id) {
		return supplierRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No supplier with id " + id));
	}

	private Campus findCampus(Long id) {
		return campusRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No campus with id " + id));
	}

	private Item findItem(Long id) {
		return itemRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No item with id " + id));
	}
}
