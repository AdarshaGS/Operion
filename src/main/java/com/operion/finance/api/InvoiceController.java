package com.operion.finance.api;

import java.time.LocalDate;
import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.finance.FeeReminderService;
import com.operion.finance.Invoice;
import com.operion.finance.InvoiceRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fees/invoices")
@RequirePermission("FEE_VIEW")
public class InvoiceController {

	private final InvoiceRepository invoiceRepository;
	private final FeeReminderService feeReminderService;

	public InvoiceController(InvoiceRepository invoiceRepository, FeeReminderService feeReminderService) {
		this.invoiceRepository = invoiceRepository;
		this.feeReminderService = feeReminderService;
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

	/** The demand view (#237) - every overdue invoice across the org, optionally narrowed
	 * to one class or one student. */
	@GetMapping("/overdue")
	public List<OverdueInvoiceResponse> overdue(
			@RequestParam(required = false) Long schoolClassId, @RequestParam(required = false) Long studentEnrollmentId) {
		LocalDate today = LocalDate.now();
		return invoiceRepository.findOverdue(today, schoolClassId, studentEnrollmentId).stream()
				.map(invoice -> OverdueInvoiceResponse.from(invoice, today))
				.toList();
	}

	/** Sends a reminder through the existing notification pipeline for each invoice's
	 * student's guardians (#237) - a distinct, higher-trust permission from plain FEE_VIEW. */
	@PostMapping("/send-reminders")
	@RequirePermission("FEE_REMINDER_SEND")
	public List<ReminderResultResponse> sendReminders(@RequestBody SendRemindersRequest request) {
		return request.invoiceIds().stream()
				.map(invoiceId -> new ReminderResultResponse(invoiceId, feeReminderService.sendOverdueReminder(findInvoice(invoiceId))))
				.toList();
	}

	private Invoice findInvoice(Long invoiceId) {
		return invoiceRepository.findById(invoiceId)
				.orElseThrow(() -> new IllegalArgumentException("No invoice with id " + invoiceId));
	}
}
