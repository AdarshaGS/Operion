package com.operion.finance.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.finance.Invoice;
import com.operion.finance.InvoiceRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fees/invoices")
@RequirePermission("FEE_VIEW")
public class InvoiceController {

	private final InvoiceRepository invoiceRepository;

	public InvoiceController(InvoiceRepository invoiceRepository) {
		this.invoiceRepository = invoiceRepository;
	}

	@GetMapping("/{invoiceId}")
	public InvoiceResponse get(@PathVariable Long invoiceId) {
		return InvoiceResponse.from(findInvoice(invoiceId));
	}

	@GetMapping
	public List<InvoiceResponse> list(@RequestParam Long studentEnrollmentId) {
		return invoiceRepository.findByStudentFeeAssignment_StudentEnrollmentId(studentEnrollmentId).stream()
				.map(InvoiceResponse::from)
				.toList();
	}

	private Invoice findInvoice(Long invoiceId) {
		return invoiceRepository.findById(invoiceId)
				.orElseThrow(() -> new IllegalArgumentException("No invoice with id " + invoiceId));
	}
}
