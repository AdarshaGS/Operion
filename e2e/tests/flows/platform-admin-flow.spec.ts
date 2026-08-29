// Platform Admin flow: the separate auth plane for staff who manage tenant organisations
// and billing (see e2e/README.md's Architecture section). Runs unauthenticated and logs in
// through the real platform login form as its first step, with the same dev-bootstrap
// credential tests/platform-admin/smoke.spec.ts also exercises.
//
// Known gap: the ticket for this flow asks for a "module catalogue" and "feature flags"
// surface with per-tenant entitlement toggles. Neither exists in this codebase today - the
// platform app only has Organisations, Plans, Subscriptions, and Invoices (see
// web/src/platform/*). A Subscription (org -> Plan) is the closest real analogue to a
// tenant "entitlement", so that's what this flow exercises and verifies end to end,
// including the one thing that IS audit-logged for it: the organisation's own STATUS_CHANGE
// entry (OrganisationService.changeStatus) - Subscription creation itself is not currently
// audit-logged (BillingService has no AuditLogService call), which this flow does not assert.

import { expect, test, type APIRequestContext } from "@playwright/test";
import { API_BASE_URL } from "../../api/client";
import { PLATFORM_ADMIN_CREDENTIALS } from "../../fixtures/roles";
import { PlatformLoginPage } from "../../pages/platform/PlatformLoginPage";
import { PlatformOrganisationDetailPage } from "../../pages/platform/OrganisationDetailPage";
import { PlatformOrganisationsPage } from "../../pages/platform/OrganisationsPage";
import { PlatformPlansPage } from "../../pages/platform/PlansPage";
import { trackDiagnostics } from "../../support/diagnostics";

async function tenantLogin(request: APIRequestContext, organisationSlug: string, email: string, password: string) {
	const response = await request.post(`${API_BASE_URL}/api/v1/auth/login`, { data: { organisationSlug, email, password } });
	expect(response.ok(), `tenant login for ${organisationSlug} should succeed`).toBe(true);
	return (await response.json()) as { token: string };
}

test("Platform Admin: manages plans, provisions a tenant, changes its status, and grants + bills a subscription", async ({
	page,
	request,
}) => {
	const diagnostics = trackDiagnostics(page);
	const run = Date.now();

	await test.step("1. Log in as Platform Admin - a separate auth plane, platform-only pages are reachable", async () => {
		const login = new PlatformLoginPage(page);
		await login.open();
		await login.login(PLATFORM_ADMIN_CREDENTIALS.email, PLATFORM_ADMIN_CREDENTIALS.password);
		await expect(page).toHaveURL(/\/platform\/organisations/); // PlatformLoginPage's own post-login target

		await page.goto("/platform/dashboard");
		await expect(page).not.toHaveURL(/\/platform\/login/);
		await expect(page.getByRole("heading", { name: /dashboard/i })).toBeVisible();
		diagnostics.assertClean("platform dashboard");
	});

	const planCode = `FLOWPLAN${run % 100000}`;
	const planName = `Flow Plan ${run}`;
	await test.step("Create a billing plan", async () => {
		const plans = new PlatformPlansPage(page);
		await plans.open();
		await plans.addPlan({ code: planCode, name: planName, pricePerStudentPerYear: 2000 });
		await expect(plans.planRow(planCode)).toBeVisible();
		diagnostics.assertClean("plan creation");
	});

	const orgSlug = `plat-flow-${run.toString(36)}`;
	const adminEmail = `owner@${orgSlug}.test`;
	const adminPassword = "E2ePassw0rd!123";
	await test.step("Provision a tenant organisation", async () => {
		const organisations = new PlatformOrganisationsPage(page);
		await organisations.open();
		await organisations.addOrganisation({
			name: `Flow Org ${run}`,
			legalName: `Flow Org ${run} Pvt Ltd`,
			slug: orgSlug,
			adminFirstName: "Owner",
			adminLastName: "FlowOrg",
			adminEmail,
			adminPassword,
		});
		await expect(organisations.organisationRow(orgSlug)).toBeVisible();
		await organisations.openOrganisation(orgSlug);
		await expect(page).toHaveURL(/\/platform\/organisations\/\d+/);
		diagnostics.assertClean("organisation provisioning");
	});

	const detail = new PlatformOrganisationDetailPage(page);

	await test.step("Change the organisation's status and confirm it's saved and audit-logged", async () => {
		await expect(detail.heading()).toHaveText(`Flow Org ${run}`);
		await expect(detail.statusChip("TRIAL")).toBeVisible();
		await detail.changeStatus("ACTIVE");
		await expect(detail.statusChip("ACTIVE")).toBeVisible();

		const { token } = await tenantLogin(request, orgSlug, adminEmail, adminPassword);
		const auditResponse = await request.get(`${API_BASE_URL}/api/v1/audit-logs`, { headers: { Authorization: `Bearer ${token}` } });
		expect(auditResponse.status()).toBe(200);
		const audit = await auditResponse.json();
		const statusChangeEntries = audit.content.filter((entry: { entityType: string; action: string }) => entry.entityType === "Organisation" && entry.action === "STATUS_CHANGE");
		expect(statusChangeEntries.length, "expected a STATUS_CHANGE audit entry for this organisation").toBeGreaterThan(0);

		diagnostics.assertClean("status change + audit check");
	});

	await test.step("Grant an entitlement (subscribe to a plan) and verify it's saved", async () => {
		await detail.startSubscription({ planLabel: planName, startDate: "2026-06-01" });
		await expect(detail.subscriptionRow(planName)).toBeVisible();
		await expect(detail.subscriptionRow(planName)).toContainText("2,000");
		diagnostics.assertClean("subscription grant");
	});

	await test.step("Generate and settle an invoice against the entitlement", async () => {
		await detail.generateInvoice({ periodStart: "2026-06-01", periodEnd: "2026-06-30", dueDate: "2026-07-05" });
		await expect(detail.invoiceRow("2026-06-01")).toBeVisible();
		await expect(detail.invoiceRow("2026-06-01")).toContainText("ISSUED");

		await detail.markInvoicePaid("2026-06-01");
		await expect(detail.invoiceRow("2026-06-01")).toContainText("PAID");

		diagnostics.assertClean("invoicing");
	});
});
