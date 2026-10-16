package com.operion.finance.api;

import com.operion.authorization.RequirePermission;
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
	private final FeeApproverPolicy feeApproverPolicy;

	public RefundController(FeeService feeService, PaymentRepository paymentRepository, InvoiceRepository invoiceRepository,
			FeeApproverPolicy feeApproverPolicy) {
		this.feeApproverPolicy = feeApproverPolicy;
		this.feeService = feeService;
		this.paymentRepository = paymentRepository;
		this.invoiceRepository = invoiceRepository;
	}

	@PostMapping
	@RequirePermission("FEE_REFUND_APPROVE")
	public RefundResponse record(@RequestBody RecordRefundRequest request) {
		RequestChecks.required(request.paymentId(), "paymentId");
		RequestChecks.required(request.invoiceId(), "invoiceId");
		Payment payment = paymentRepository.findById(request.paymentId())
				.orElseThrow(() -> new IllegalArgumentException("No payment with id " + request.paymentId()));
		Invoice invoice = invoiceRepository.findById(request.invoiceId())
				.orElseThrow(() -> new IllegalArgumentException("No invoice with id " + request.invoiceId()));

		feeApproverPolicy.requireEligible(request.approvedBy());
		Refund refund = feeService.recordRefund(payment, invoice, request.amount(), request.reason(), request.approvedBy(),
				request.refundDate(), request.proofFileReference(), request.proofFileName());
		return RefundResponse.from(refund);
	}
}
