import type { ActivityResponse } from "./api/activity";
import type { OrganisationResponse } from "./api/organisations";
import type { PlanResponse } from "./api/plans";
import type { PlatformInvoiceResponse } from "./api/platformInvoices";
import type { SubscriptionResponse } from "./api/subscriptions";

export interface ActivityLookups {
	organisations: OrganisationResponse[];
	plans: PlanResponse[];
	subscriptions: SubscriptionResponse[];
	invoices: PlatformInvoiceResponse[];
}

function currency(amount: number): string {
	return `₹${amount.toLocaleString("en-IN")}`;
}

/** Renders one audit_logs row (entity_type/action, no before/after payload - see
 * PlatformAuditLogController's javadoc on why that's deliberately not exposed) into the
 * one-line description the mockup's "Recent activity" timeline shows, by cross-referencing
 * the entity ids against data the dashboard/activity page already has loaded. */
export function describeActivity(entry: ActivityResponse, { organisations, plans, subscriptions, invoices }: ActivityLookups): string {
	const orgName = (id: number | null) => organisations.find((org) => org.id === id)?.name ?? "An organisation";

	switch (entry.entityType) {
		case "Organisation": {
			const name = orgName(entry.entityId);
			return entry.action === "CREATE" ? `${name} created` : `${name} status changed`;
		}
		case "Subscription": {
			const subscription = subscriptions.find((s) => s.id === entry.entityId);
			const name = orgName(subscription?.organisationId ?? entry.organisationId);
			const plan = plans.find((p) => p.id === subscription?.planId);
			return plan ? `${name} subscribed to ${plan.name}` : `${name} started a subscription`;
		}
		case "PlatformInvoice": {
			const invoice = invoices.find((i) => i.id === entry.entityId);
			const name = orgName(invoice?.organisationId ?? entry.organisationId);
			if (entry.action === "STATUS_CHANGE") {
				return invoice ? `Payment of ${currency(invoice.amount)} received from ${name}` : `Payment received from ${name}`;
			}
			return invoice ? `Invoice generated for ${name} (${currency(invoice.amount)})` : `Invoice generated for ${name}`;
		}
		case "Plan": {
			const plan = plans.find((p) => p.id === entry.entityId);
			const name = plan?.name ?? "A plan";
			return entry.action === "CREATE" ? `Plan "${name}" created` : `Plan "${name}" status changed`;
		}
		default:
			return `${entry.entityType} ${entry.action.toLowerCase().replace("_", " ")}`;
	}
}
