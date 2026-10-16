package com.operion.organisation.rollover;

import com.operion.modules.AppModule;
import com.operion.organisation.AcademicYear;

/**
 * One module's share of "copy forward": what it carries from one academic year's setup into
 * the next. Each module adds its own bean, so a new year-scoped structure joins the rollover
 * without touching the service. A contributor copies structure only (classes, assignments,
 * fee amounts), never activity (attendance, invoices, payments, marks), and is idempotent:
 * anything already present in the target year is counted, not duplicated, so a run can be
 * repeated or limited to a few steps. Contributors never commit - the service owns the
 * transaction, which is what lets the same code serve both the preview and the real run.
 */
public interface RolloverContributor {

	/** Stable identifier used in requests and reports. */
	String key();

	String label();

	/** Only offered and run while this module is enabled for the organisation. */
	AppModule module();

	/** Lower runs first in one run; steps still work in any combination because each looks the target up by itself. */
	int order();

	RolloverStep run(AcademicYear source, AcademicYear target, RolloverOptions options);
}
