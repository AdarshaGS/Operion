import { expect, test } from "@playwright/test";
import { api, ApiError } from "../../api/client";
import { login, provisionOrganisation } from "../../api/organisations";
import { FRONTEND_BASE_URL } from "../../env";

// School Starter Pack: answering a few questions picks the modules (feature flags are written
// once), the template is applied only for those modules, and the go-live check covers exactly
// the modules that are on. Runs against its own freshly provisioned organisation.

interface MeResponse {
	disabledModules: string[];
}

interface Recommendation {
	modules: { code: string; enabled: boolean; locked: boolean; reason: string }[];
}

interface GoLive {
	ready: boolean;
	groups: { code: string; findings: { message: string }[] }[];
	hiddenModules: string[];
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

const BASE = "/api/v1/organisations/settings/school-template";

test("starter pack: needs choose the modules once, template follows, go-live checks only what is on", async ({ browser }) => {
	const run = Date.now();
	const slug = `sp-${run}`;
	const password = "StarterPack!123";
	await provisionOrganisation({
		name: `Pack Org ${run}`,
		legalName: `Pack Org ${run} Pvt Ltd`,
		slug,
		adminEmail: `owner@${slug}.test`,
		adminPassword: password,
		adminFirstName: "Pack",
		adminLastName: "Owner",
	});
	const owner = await login(slug, `owner@${slug}.test`, password);

	// A fresh organisation is offered the questions.
	const needs = await api.get<{ questions: { key: string }[]; canSelectModules: boolean }>(`${BASE}/needs`, owner.token);
	expect(needs.canSelectModules).toBe(true);
	expect(needs.questions.map((question) => question.key)).toContain("TRANSPORT");

	// Preview writes nothing; Payroll with HR declined forces HR on and says why.
	const preview = await api.post<Recommendation>(`${BASE}/recommend`, { answers: { PAYROLL: true, HR: false } }, owner.token);
	const hr = preview.modules.find((module) => module.code === "HR");
	expect(hr).toMatchObject({ enabled: true, locked: true });
	expect((await api.get<MeResponse>("/api/v1/auth/me", owner.token)).disabledModules).toEqual([]);

	// Apply: no buses, no library, no payroll.
	await api.post(`${BASE}/apply`, { answers: { TRANSPORT: false, LIBRARY: false, PAYROLL: false } }, owner.token);
	const disabled = (await api.get<MeResponse>("/api/v1/auth/me", owner.token)).disabledModules;
	expect(disabled).toEqual(expect.arrayContaining(["TRANSPORT", "LIBRARY", "PAYROLL"]));
	expect(disabled).not.toContain("FEES");
	expect(await statusOf(api.get("/api/v1/library/books", owner.token))).toBe(403);

	// The choice can be made once; after that only the platform owner changes modules.
	expect(await statusOf(api.post(`${BASE}/apply`, { answers: { TRANSPORT: true } }, owner.token))).toBe(409);
	expect((await api.get<{ canSelectModules: boolean }>(`${BASE}/needs`, owner.token)).canSelectModules).toBe(false);

	// The template skipped what belongs to a module that is off.
	const designations = await api.get<{ name: string }[]>("/api/v1/designations", owner.token);
	const names = designations.map((designation) => designation.name);
	expect(names).toContain("Teacher");
	expect(names).not.toContain("Driver");
	expect(names).not.toContain("Librarian");

	// Go-live covers exactly the modules that are on.
	const report = await api.get<GoLive>("/api/v1/organisations/settings/go-live", owner.token);
	const covered = report.groups.map((group) => group.code);
	expect(covered).toContain("FEES");
	expect(covered).not.toContain("TRANSPORT");
	expect(report.hiddenModules).toEqual(expect.arrayContaining(["Transport", "Library"]));

	// And the page renders it for the Owner.
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
	const page = await context.newPage();
	await page.goto("/setup/go-live");
	await expect(page.getByRole("heading", { name: "Go-live check" })).toBeVisible();
	await expect(page.getByText("Not checked because they are switched off for your school")).toBeVisible();
	await expect(page.getByText("Transport", { exact: true })).toHaveCount(0);

	// The Review page no longer lists a row for a module that is off... and offers the check.
	await page.goto("/setup/review");
	await expect(page.getByRole("button", { name: "Run go-live check" })).toBeVisible();
	await context.close();
});

test("starter pack dialog: school admin answers the questions in the UI and the sidebar follows", async ({ browser }) => {
	const run = Date.now();
	const slug = `spu-${run}`;
	const password = "StarterPack!123";
	await provisionOrganisation({
		name: `Pack UI Org ${run}`,
		legalName: `Pack UI Org ${run} Pvt Ltd`,
		slug,
		adminEmail: `owner@${slug}.test`,
		adminPassword: password,
		adminFirstName: "Pack",
		adminLastName: "Owner",
	});
	const owner = await login(slug, `owner@${slug}.test`, password);
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
	const page = await context.newPage();
	await page.goto("/settings/school-template");
	await page.getByRole("button", { name: "Set up with starter pack" }).click();
	const dialog = page.getByRole("dialog", { name: "Set up your school" });
	await expect(dialog).toBeVisible();

	// Transport needs Fees: with fees declined, asking for buses switches Fees back on and says why.
	await expect(dialog.getByText(/Hidden for your school/)).toBeVisible();
	await dialog.getByRole("switch", { name: /Do you collect fees/ }).uncheck();
	await dialog.getByRole("switch", { name: /Do you run school buses/ }).check();
	await expect(dialog.getByText(/required by transport/i)).toBeVisible();
	await dialog.getByRole("switch", { name: /Do you run school buses/ }).uncheck();
	await dialog.getByRole("switch", { name: /Do you collect fees/ }).check();

	await dialog.getByRole("button", { name: "Apply starter pack" }).click();
	await expect(dialog).toBeHidden();
	await expect(page.getByRole("button", { name: "Apply school template" })).toBeVisible();

	// The sidebar reflects the choice without a re-login.
	await page.goto("/students");
	await expect(page.getByRole("button", { name: "Students", exact: true })).toBeVisible();
	await expect(page.getByRole("button", { name: "Transport", exact: true })).toHaveCount(0);
	await context.close();
});
