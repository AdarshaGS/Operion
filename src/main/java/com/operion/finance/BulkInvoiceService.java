package com.operion.finance;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.operion.common.InvalidRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Generates every invoice that has fallen due across a year (optionally one class) in one
 * action, instead of one assignment + installment at a time. Deliberately not
 * {@code @Transactional}: each invoice goes through FeeService.generateInvoice in its own
 * transaction, so one bad row (inactive structure, a concurrent duplicate) is reported and
 * skipped rather than rolling back the whole batch - same per-row isolation as the importers.
 * Re-running is safe: an (assignment, installment) pair that already has an invoice is skipped.
 */
@Service
public class BulkInvoiceService {

	private static final Logger log = LoggerFactory.getLogger(BulkInvoiceService.class);

	private final StudentFeeAssignmentRepository assignmentRepository;
	private final FeeStructureInstallmentRepository installmentRepository;
	private final InvoiceRepository invoiceRepository;
	private final FeeService feeService;
	private final FeeReminderService feeReminderService;

	public BulkInvoiceService(StudentFeeAssignmentRepository assignmentRepository,
			FeeStructureInstallmentRepository installmentRepository, InvoiceRepository invoiceRepository, FeeService feeService,
			FeeReminderService feeReminderService) {
		this.assignmentRepository = assignmentRepository;
		this.installmentRepository = installmentRepository;
		this.invoiceRepository = invoiceRepository;
		this.feeService = feeService;
		this.feeReminderService = feeReminderService;
	}

	public Result generateDue(Long academicYearId, Long schoolClassId, LocalDate dueOnOrBefore, boolean dryRun) {
		if (academicYearId == null) {
			throw new InvalidRequestException("academicYearId is required");
		}
		if (dueOnOrBefore == null) {
			throw new InvalidRequestException("dueOnOrBefore is required");
		}
		int alreadyInvoiced = 0;
		int generated = 0;
		List<Long> invoiceIds = new ArrayList<>();
		List<Failure> failures = new ArrayList<>();
		int pending = 0;

		for (StudentFeeAssignment assignment : assignmentRepository.findActiveForBulkInvoicing(academicYearId, schoolClassId)) {
			for (FeeStructureInstallment installment : installmentRepository
					.findByFeeStructureIdOrderByInstallmentNumber(assignment.getFeeStructure().getId())) {
				if (installment.getDueDate().isAfter(dueOnOrBefore)) {
					continue;
				}
				if (invoiceRepository.findByStudentFeeAssignmentIdAndFeeStructureInstallmentId(assignment.getId(), installment.getId())
						.isPresent()) {
					alreadyInvoiced++;
					continue;
				}
				pending++;
				if (dryRun) {
					continue;
				}
				try {
					Invoice invoice = feeService.generateInvoice(assignment, installment);
					generated++;
					invoiceIds.add(invoice.getId());
					notifyQuietly(invoice);
				} catch (RuntimeException ex) {
					failures.add(new Failure(assignment.getId(), installment.getId(), ex.getMessage()));
				}
			}
		}
		return new Result(dryRun, pending, generated, alreadyInvoiced, invoiceIds, failures);
	}

	/** Same rule as the single-invoice endpoint: the invoice is committed, a failed guardian nudge must not undo it. */
	private void notifyQuietly(Invoice invoice) {
		try {
			feeReminderService.notifyInvoiceGenerated(invoice);
		} catch (RuntimeException ex) {
			log.warn("Invoice {} generated but its guardian notification failed", invoice.getInvoiceNumber(), ex);
		}
	}

	/** {@code toGenerate} counts invoices that were (or, on a dry run, would be) created. */
	public record Result(boolean dryRun, int toGenerate, int generated, int alreadyInvoiced, List<Long> invoiceIds,
			List<Failure> failures) {
	}

	public record Failure(Long assignmentId, Long installmentId, String reason) {
	}
}
