package com.operion.sales.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.inventory.Customer;
import com.operion.inventory.CustomerRepository;
import com.operion.inventory.Item;
import com.operion.inventory.ItemRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.sales.PaymentMethod;
import com.operion.sales.Sale;
import com.operion.sales.SaleLineRepository;
import com.operion.sales.SalePaymentRepository;
import com.operion.sales.SaleRepository;
import com.operion.sales.SaleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sales")
@RequirePermission("SALES_VIEW")
public class SaleController {

	private final SaleService saleService;
	private final SaleRepository saleRepository;
	private final SaleLineRepository saleLineRepository;
	private final SalePaymentRepository salePaymentRepository;
	private final CustomerRepository customerRepository;
	private final CampusRepository campusRepository;
	private final ItemRepository itemRepository;

	public SaleController(SaleService saleService, SaleRepository saleRepository, SaleLineRepository saleLineRepository,
			SalePaymentRepository salePaymentRepository, CustomerRepository customerRepository, CampusRepository campusRepository,
			ItemRepository itemRepository) {
		this.saleService = saleService;
		this.saleRepository = saleRepository;
		this.saleLineRepository = saleLineRepository;
		this.salePaymentRepository = salePaymentRepository;
		this.customerRepository = customerRepository;
		this.campusRepository = campusRepository;
		this.itemRepository = itemRepository;
	}

	@PostMapping
	@RequirePermission("SALES_MANAGE")
	public SaleResponse create(@RequestBody CreateSaleRequest request) {
		Customer customer = findCustomer(request.customerId());
		Campus campus = findCampus(request.campusId());
		List<SaleService.LineInput> lines = request.lines().stream()
				.map(line -> new SaleService.LineInput(findItem(line.itemId()), line.quantity(), line.unitPrice()))
				.toList();
		Sale sale = saleService.createSale(customer, campus, request.saleDate(), lines);
		return SaleResponse.from(sale);
	}

	@GetMapping
	public List<SaleResponse> list(@RequestParam(required = false) Long customerId) {
		List<Sale> sales = customerId != null ? saleRepository.findByCustomerIdOrderByCreatedAtDesc(customerId) : saleRepository.findAll();
		return sales.stream().map(SaleResponse::from).toList();
	}

	@GetMapping("/{id}")
	public SaleDetailResponse detail(@PathVariable Long id) {
		return toDetailResponse(findSale(id));
	}

	@PostMapping("/{id}/payments")
	@RequirePermission("SALES_MANAGE")
	public SaleDetailResponse recordPayment(@PathVariable Long id, @RequestBody RecordSalePaymentRequest request) {
		Sale sale = findSale(id);
		saleService.recordPayment(sale, PaymentMethod.valueOf(request.paymentMethod()), request.amount(), request.paidAt());
		return toDetailResponse(sale);
	}

	private SaleDetailResponse toDetailResponse(Sale sale) {
		List<SaleLineResponse> lines = saleLineRepository.findBySaleId(sale.getId()).stream().map(SaleLineResponse::from).toList();
		List<SalePaymentResponse> payments = salePaymentRepository.findBySaleIdOrderByPaidAtDesc(sale.getId()).stream()
				.map(SalePaymentResponse::from).toList();
		return SaleDetailResponse.from(sale, lines, payments);
	}

	private Sale findSale(Long id) {
		return saleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No sale with id " + id));
	}

	private Customer findCustomer(Long id) {
		return customerRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No customer with id " + id));
	}

	private Campus findCampus(Long id) {
		return campusRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No campus with id " + id));
	}

	private Item findItem(Long id) {
		return itemRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No item with id " + id));
	}
}
