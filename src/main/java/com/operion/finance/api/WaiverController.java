package com.operion.finance.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.finance.FeeService;
import com.operion.finance.Invoice;
import com.operion.finance.InvoiceRepository;
import com.operion.finance.Waiver;
import com.operion.finance.WaiverRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fees/waivers")
public class WaiverController {

	private final FeeService feeService;
	private final InvoiceRepository invoiceRepository;
	private final WaiverRepository waiverRepository;

	public WaiverController(FeeService feeService, InvoiceRepository invoiceRepository, WaiverRepository waiverRepository) {
		this.feeService = feeService;
		this.invoiceRepository = invoiceRepository;
		this.waiverRepository = waiverRepository;
	}

	@PostMapping
	@RequirePermission("FEE_WAIVER_APPROVE")
	public WaiverResponse record(@RequestBody RecordWaiverRequest request) {
		Invoice invoice = invoiceRepository.findById(request.invoiceId())
				.orElseThrow(() -> new IllegalArgumentException("No invoice with id " + request.invoiceId()));

		Waiver waiver = feeService.recordWaiver(invoice, request.amount(), request.reason(), request.approvedBy(), request.waiverDate());
		return WaiverResponse.from(waiver);
	}

	@GetMapping
	@RequirePermission("FEE_VIEW")
	public List<WaiverResponse> list(@RequestParam Long invoiceId) {
		return waiverRepository.findByInvoiceId(invoiceId).stream().map(WaiverResponse::from).toList();
	}
}
