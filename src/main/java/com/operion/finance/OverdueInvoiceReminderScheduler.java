package com.operion.finance;

import java.time.LocalDate;

import com.operion.common.SchedulerHeartbeat;
import com.operion.common.TenantContext;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The "invoice overdue -> automatic reminder" half of #165 - a DB-backed daily poller,
 * same convention as NotificationDispatchWorker/ScheduledAnnouncementPublisher (see their
 * class docs for the "no Kafka" reasoning and the per-tenant TenantContext looping they
 * share). Fires FeeReminderService.sendOverdueReminder at most once per invoice -
 * overdueReminderSentAt is stamped right after sending so a later tick never re-notifies
 * the same still-unpaid invoice (a deliberate v1 simplicity call: no recurring cadence).
 */
@Component
public class OverdueInvoiceReminderScheduler {

	private static final long POLL_INTERVAL_MILLIS = 24 * 60 * 60_000;

	private final OrganisationRepository organisationRepository;
	private final InvoiceRepository invoiceRepository;
	private final FeeReminderService feeReminderService;
	private final SchedulerHeartbeat schedulerHeartbeat;

	public OverdueInvoiceReminderScheduler(OrganisationRepository organisationRepository, InvoiceRepository invoiceRepository,
			FeeReminderService feeReminderService, SchedulerHeartbeat schedulerHeartbeat) {
		this.organisationRepository = organisationRepository;
		this.invoiceRepository = invoiceRepository;
		this.feeReminderService = feeReminderService;
		this.schedulerHeartbeat = schedulerHeartbeat;
	}

	@Scheduled(fixedDelay = POLL_INTERVAL_MILLIS)
	public void sendOverdueReminders() {
		schedulerHeartbeat.tick();
		LocalDate today = LocalDate.now();
		for (Organisation organisation : organisationRepository.findAll()) {
			TenantContext.set(organisation.getId(), null);
			try {
				for (Invoice invoice : invoiceRepository.findOverdueWithoutReminder(today)) {
					feeReminderService.sendOverdueReminder(invoice);
					invoice.markOverdueReminderSent();
					invoiceRepository.save(invoice);
				}
			} finally {
				TenantContext.clear();
			}
		}
	}
}
