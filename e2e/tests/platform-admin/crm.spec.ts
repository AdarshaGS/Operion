import { expect, test } from "@playwright/test";

test("platform CRM: lead pipeline, contacts, activity, follow-ups, conversion and dashboard", async ({ page }) => {
	const run = Date.now();
	const schoolName = `E2E CRM Lead ${run}`;
	const contactEmail = `principal-${run}@school.test`;

	// --- Leads pipeline: create ---
	await page.goto("/platform/crm/leads");
	await page.getByRole("button", { name: "New lead" }).click();
	await page.getByLabel("School name").fill(schoolName);
	await page.getByLabel("Estimated students").fill("450");
	await page.getByRole("button", { name: "Create" }).click();
	const card = page.getByText(schoolName, { exact: true });
	await expect(card).toBeVisible();

	// --- Lead detail ---
	await card.click();
	await expect(page).toHaveURL(/\/platform\/crm\/leads\/\d+$/);
	await expect(page.getByRole("heading", { name: schoolName })).toBeVisible();
	await expect(page.getByRole("button", { name: "Convert to Organisation" })).toHaveCount(0);

	// contact
	await page.getByRole("button", { name: "Add contact" }).click();
	await page.getByLabel("Name").fill("Priya Sharma");
	await page.getByLabel("Role / title").fill("Principal");
	await page.getByLabel("Email").fill(contactEmail);
	await page.getByRole("button", { name: "Save" }).click();
	await expect(page.getByRole("row").filter({ hasText: "Priya Sharma" })).toContainText("Principal");

	// activity timeline (insert-only)
	await page.getByLabel("What happened?").fill("Intro call went well");
	await page.getByRole("button", { name: "Log", exact: true }).click();
	await expect(page.getByText("Intro call went well")).toBeVisible();

	// follow-up task
	await page.getByRole("button", { name: "Add task" }).click();
	await page.getByLabel("Title").fill("Send proposal");
	await page.getByRole("button", { name: "Add", exact: true }).click();
	await expect(page.getByText("Send proposal")).toBeVisible();

	// --- Convert only becomes available once WON ---
	await page.getByRole("button", { name: "Won", exact: true }).click();
	await page.getByRole("button", { name: "Convert to Organisation" }).click();
	await expect(page.getByLabel("Organisation name")).toHaveValue(schoolName);
	await expect(page.getByLabel("Admin first name")).toHaveValue("Priya");
	await expect(page.getByLabel("Admin email")).toHaveValue(contactEmail);
	await page.getByLabel("Admin password").fill("PlatformPassw0rd!123");
	await page.getByRole("button", { name: "Convert", exact: true }).click();

	await expect(page.getByText("Converted —")).toBeVisible();
	await expect(page.getByRole("button", { name: "Convert to Organisation" })).toHaveCount(0);
	await expect(page.getByText(/Converted to organisation/)).toBeVisible();

	// --- Organisation detail carries the same contact/activity/task sections, scoped to the org ---
	await page.getByRole("link", { name: "view the organisation" }).click();
	await expect(page).toHaveURL(/\/platform\/organisations\/\d+$/);
	await expect(page.getByText("No contacts yet.")).toBeVisible();
	await page.getByRole("button", { name: "Add contact" }).click();
	await page.getByLabel("Name").fill("Omar Finance");
	await page.getByRole("button", { name: "Save" }).click();
	await expect(page.getByRole("row").filter({ hasText: "Omar Finance" })).toBeVisible();

	// --- Dashboard: pipeline metrics + due follow-ups ---
	await page.goto("/platform/dashboard");
	await expect(page.getByRole("heading", { name: "Sales pipeline" })).toBeVisible();
	await expect(page.getByText("Win rate")).toBeVisible();
	await expect(page.getByText("Send proposal")).toBeVisible();
});
