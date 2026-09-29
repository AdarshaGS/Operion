-- #165: tracks whether OverdueInvoiceReminderScheduler has already sent the one-time
-- automatic overdue reminder for this invoice, so a later poll never re-fires it.
ALTER TABLE invoices ADD COLUMN overdue_reminder_sent_at DATETIME(6) NULL;
