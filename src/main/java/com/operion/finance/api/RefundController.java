package com.operion.finance.api;

import com.operion.finance.FeeService;
import com.operion.finance.Invoice;
import com.operion.finance.InvoiceRepository;
import com.operion.finance.Payment;
import com.operion.finance.PaymentRepository;
import com.operion.finance.Refund;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fees/refunds")
public class RefundController {

	private final FeeService feeService;
	private final PaymentRepository paymentRepository;
	private final InvoiceRepository invoiceRepository;

	public RefundController(FeeService feeService, PaymentRepository paymentRepository, InvoiceRepository invoiceRepository) {
		this.feeService = feeService;
		this.paymentRepository = paymentRepository;
		this.invoiceRepository = invoiceRepository;
	}

	@PostMapping
	public RefundResponse record(@RequestBody RecordRefundRequest request) {
		Payment payment = paymentRepository.findById(request.paymentId())
				.orElseThrow(() -> new IllegalArgumentException("No payment with id " + request.paymentId()));
		Invoice invoice = invoiceRepository.findById(request.invoiceId())
				.orElseThrow(() -> new IllegalArgumentException("No invoice with id " + request.invoiceId()));

		Refund refund = feeService.recordRefund(
				payment, invoice, request.amount(), request.reason(), request.approvedBy(), request.refundDate());
		return RefundResponse.from(refund);
	}
}
