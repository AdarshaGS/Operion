package com.operion.finance.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.finance.Adjustment;
import com.operion.finance.AdjustmentRepository;
import com.operion.finance.FeeService;
import com.operion.finance.Invoice;
import com.operion.finance.InvoiceRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fees/adjustments")
public class AdjustmentController {

	private final FeeService feeService;
	private final InvoiceRepository invoiceRepository;
	private final AdjustmentRepository adjustmentRepository;

	public AdjustmentController(FeeService feeService, InvoiceRepository invoiceRepository, AdjustmentRepository adjustmentRepository) {
		this.feeService = feeService;
		this.invoiceRepository = invoiceRepository;
		this.adjustmentRepository = adjustmentRepository;
	}

	@PostMapping
	@RequirePermission("FEE_ADJUSTMENT_MANAGE")
	public AdjustmentResponse record(@RequestBody RecordAdjustmentRequest request) {
		Invoice invoice = invoiceRepository.findById(request.invoiceId())
				.orElseThrow(() -> new IllegalArgumentException("No invoice with id " + request.invoiceId()));

		Adjustment adjustment = feeService.recordAdjustment(invoice, request.amount(), request.reason(), request.approvedBy(), request.adjustmentDate());
		return AdjustmentResponse.from(adjustment);
	}

	@GetMapping
	@RequirePermission("FEE_VIEW")
	public List<AdjustmentResponse> list(@RequestParam Long invoiceId) {
		return adjustmentRepository.findByInvoiceId(invoiceId).stream().map(AdjustmentResponse::from).toList();
	}
}
