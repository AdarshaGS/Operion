import { expect, test } from "@playwright/test";
import { api, ApiError } from "../../api/client";
import { login, provisionOrganisation } from "../../api/organisations";
import { FRONTEND_BASE_URL } from "../../env";

// Feature flags (GitHub #198): the platform owner switches a module off for one
// organisation, and it must disappear from that organisation's UI *and* be refused by the
// API for an Owner who otherwise bypasses every permission check. Runs against its own
// freshly provisioned org so toggling never affects the shared role-fixture org.

interface MeResponse {
	disabledModules: string[];
}

async function statusOf(call: Promise<unknown>): Promise<number> {
	try {
		await call;
		return 200;
	} catch (error) {
		if (error instanceof ApiError) {
			return error.status;
		}
		throw error;
	}
}

test("platform owner switches a feature off: hidden in the UI, blocked in the API, restorable", async ({ page, browser }) => {
	const run = Date.now();
	const slug = `ff-${run}`;
	const password = "FeatureFlag!123";
	const organisation = await provisionOrganisation({
		name: `Flag Org ${run}`,
		legalName: `Flag Org ${run} Pvt Ltd`,
		slug,
		adminEmail: `owner@${slug}.test`,
		adminPassword: password,
		adminFirstName: "Flag",
		adminLastName: "Owner",
	});
	const owner = await login(slug, `owner@${slug}.test`, password);

	// Baseline: only the core staff modules are on; Library is optional and starts off.
	const defaultOff = (await api.get<MeResponse>("/api/v1/auth/me", owner.token)).disabledModules;
	expect(defaultOff).toEqual(expect.arrayContaining(["LIBRARY", "REPORTS", "PAYROLL"]));
	expect(defaultOff).not.toContain("ATTENDANCE");
	expect(await statusOf(api.get("/api/v1/library/books", owner.token))).toBe(403);

	// An organisation Owner has no way to flip it: the platform plane rejects a tenant token.
	expect(
		await statusOf(api.put(`/api/v1/platform/organisations/${organisation.id}/modules/LIBRARY`, { enabled: true }, owner.token)),
	).toBeGreaterThanOrEqual(401);

	// Switch Library on, then off again, through the real platform-admin UI.
	await page.goto(`/platform/organisations/${organisation.id}`);
	const libraryToggle = page.getByRole("switch", { name: "Library enabled" });
	await expect(libraryToggle).not.toBeChecked();
	await libraryToggle.click(); // controlled: flips only once the API call returns
	await expect(libraryToggle).toBeChecked();
	await expect.poll(async () => (await api.get<MeResponse>("/api/v1/auth/me", owner.token)).disabledModules).not.toContain("LIBRARY");
	expect(await statusOf(api.get("/api/v1/library/books", owner.token))).toBe(200);
	await libraryToggle.click();
	await expect(libraryToggle).not.toBeChecked();

	// Backend now refuses it - for the Owner - while other modules keep working.
	await expect.poll(async () => (await api.get<MeResponse>("/api/v1/auth/me", owner.token)).disabledModules).toEqual(defaultOff);
	expect(await statusOf(api.get("/api/v1/library/books", owner.token))).toBe(403);
	expect(await statusOf(api.get("/api/v1/students", owner.token))).toBe(200);

	// The organisation's own UI no longer offers it.
	const context = await browser.newContext({
		baseURL: FRONTEND_BASE_URL,
		storageState: {
			cookies: [],
			origins: [
				{
					origin: FRONTEND_BASE_URL,
					localStorage: [
						{
							name: "operion.session",
							value: JSON.stringify({
								token: owner.token,
								expiresAt: owner.expiresAt,
								userId: owner.userId,
								organisationId: owner.organisationId,
							}),
						},
					],
				},
			],
		},
	});
	const tenantPage = await context.newPage();
	await tenantPage.goto("/students");
	await expect(tenantPage.getByRole("button", { name: "Students", exact: true })).toBeVisible();
	await expect(tenantPage.getByRole("button", { name: "Library", exact: true })).toHaveCount(0);
	await tenantPage.goto("/library");
	await expect(tenantPage.getByText("This feature is not enabled for your organisation")).toBeVisible();

	// Switching it back on restores everything, nothing was deleted.
	await libraryToggle.click();
	await expect(libraryToggle).toBeChecked();
	await expect.poll(async () => (await api.get<MeResponse>("/api/v1/auth/me", owner.token)).disabledModules).not.toContain("LIBRARY");
	expect(await statusOf(api.get("/api/v1/library/books", owner.token))).toBe(200);
	await tenantPage.goto("/students");
	await expect(tenantPage.getByRole("button", { name: "Library", exact: true })).toBeVisible();

	await context.close();
});
