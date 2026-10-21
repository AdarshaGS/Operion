import { readFileSync } from "node:fs";
import { join } from "node:path";
import { expect, test } from "@playwright/test";
import { api, ApiError } from "../../../api/client";
import { login } from "../../../api/organisations";
import { platformLogin } from "../../../api/platformAuth";
import { AUTH_DIR } from "../../../env";
import { PLATFORM_ADMIN_CREDENTIALS } from "../../../fixtures/roles";
import { tokenFor } from "../../../support/auth";

// Teacher performance scorecard: an opt-in module the platform owner must switch on, whose
// metrics the organisation then chooses. Uses the shared org's seeded teacher (they have an
// ACTIVE section assignment) and gives them staff attendance on four recent days, one LATE.

// Both tests change the same organisation's module and metric settings, so they must not run side by side.
test.describe.configure({ mode: "serial" });

function isoDaysAgo(days: number): string {
	const date = new Date();
	date.setDate(date.getDate() - days);
	return date.toISOString().slice(0, 10);
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

test("the module is off until the platform owner enables it, then metrics are chosen and scored", async ({ page }) => {
	const seed = JSON.parse(readFileSync(join(AUTH_DIR, "seed.json"), "utf-8"));
	const owner = await login(seed.organisationSlug, seed.ownerEmail, seed.ownerPassword);

	// Off by default: even the Owner (who bypasses permissions) is refused.
	expect(await statusOf(api.get("/api/v1/performance/metrics", owner.token))).toBe(403);

	const platform = await platformLogin(PLATFORM_ADMIN_CREDENTIALS.email, PLATFORM_ADMIN_CREDENTIALS.password);
	await api.put(`/api/v1/platform/organisations/${owner.organisationId}/modules/PERFORMANCE`, { enabled: true }, platform.token);

	// Nothing is tracked until the organisation opts in.
	const initial = await api.get<{ metric: string; enabled: boolean }[]>("/api/v1/performance/metrics", owner.token);
	expect(initial.length).toBeGreaterThan(0);
	expect(initial.every((m) => !m.enabled)).toBe(true);

	for (const [daysAgo, status] of [[1, "PRESENT"], [2, "LATE"], [3, "PRESENT"], [4, "PRESENT"]] as const) {
		await api.post(
			"/api/v1/attendance/staff",
			{ personId: seed.teacherPersonId, campusId: seed.campusId, attendanceDate: isoDaysAgo(daysAgo), status },
			owner.token,
		);
	}

	await page.goto("/performance");
	await page.getByRole("tab", { name: "Scorecard" }).click();
	await expect(page.getByText("No metrics are being tracked yet")).toBeVisible();

	await page.getByRole("tab", { name: "Metrics" }).click();
	const punctualityRow = page.getByRole("row", { name: /Punctuality/ });
	await punctualityRow.getByRole("switch").check();
	await punctualityRow.getByRole("button", { name: "Save" }).click();
	await expect(page.getByText("Punctuality saved")).toBeVisible();

	await page.getByRole("tab", { name: "Scorecard" }).click();
	// 3 of 4 attended days were on time, against a 100% target.
	await expect(page.getByRole("row", { name: /75\.0%/ })).toBeVisible();

	// A bad weight is refused by the API with a readable message.
	expect(
		await statusOf(api.put("/api/v1/performance/metrics/STAFF_PUNCTUALITY", { enabled: true, weight: 0, target: 100 }, owner.token)),
	).toBe(400);

	// Restore the default so a re-run against the same DB starts clean.
	await api.put("/api/v1/performance/metrics/STAFF_PUNCTUALITY", { enabled: false, weight: 1, target: 100 }, owner.token);
	await api.put(`/api/v1/platform/organisations/${owner.organisationId}/modules/PERFORMANCE`, { enabled: false }, platform.token);
});

test("appraisal cycle: a reviewer rates and sets a goal, and the teacher sees only what the rules allow", async ({ page }) => {
	const seed = JSON.parse(readFileSync(join(AUTH_DIR, "seed.json"), "utf-8"));
	const owner = await login(seed.organisationSlug, seed.ownerEmail, seed.ownerPassword);
	const teacherToken = tokenFor("teacher");
	const platform = await platformLogin(PLATFORM_ADMIN_CREDENTIALS.email, PLATFORM_ADMIN_CREDENTIALS.password);
	await api.put(`/api/v1/platform/organisations/${owner.organisationId}/modules/PERFORMANCE`, { enabled: true }, platform.token);
	await api.put("/api/v1/performance/metrics/STAFF_PRESENCE", { enabled: true, weight: 1, target: 100 }, owner.token);

	// Teachers see nothing of this until the organisation switches self-view on.
	await api.put("/api/v1/performance/settings", { teacherSelfView: false }, owner.token);
	expect(await statusOf(api.get("/api/v1/performance/me/scorecard?from=" + isoDaysAgo(30) + "&to=" + isoDaysAgo(0), teacherToken))).toBe(403);

	// The administrator creates a cycle in the UI; the dashboard then lists the teacher as pending.
	const cycleName = `E2E cycle ${Date.now()}`;
	await page.goto("/performance");
	await expect(page.getByRole("heading", { name: "Performance", level: 1 })).toBeVisible();
	await expect(page.getByText("No appraisal cycles yet").first()).toBeVisible();
	await page.getByRole("button", { name: "Create appraisal cycle" }).first().click();
	const createDialog = page.getByRole("dialog");
	await createDialog.getByLabel("Cycle name").fill(cycleName);
	await createDialog.getByLabel("Review period starts").fill(isoDaysAgo(30));
	await createDialog.getByLabel("Review period ends").fill(isoDaysAgo(0));
	await createDialog.getByRole("button", { name: "Create cycle" }).click();
	await expect(createDialog).toBeHidden();

	await expect(page.getByText("Pending reviews")).toBeVisible();
	const staffRow = page.getByRole("table", { name: "Staff performance" }).locator("tbody tr").first();
	await expect(staffRow.getByText("Pending", { exact: true })).toBeVisible();
	await expect(staffRow.getByText("No employee ID")).toBeVisible();
	const pendingBefore = await api.get<{ cycle: { id: number }; summary: { totalStaff: number; pending: number; completed: number } }>(
		`/api/v1/performance/cycles/${(await api.get<{ id: number; name: string }[]>("/api/v1/performance/cycles", owner.token)).find((c) => c.name === cycleName)!.id}/overview`,
		owner.token,
	);
	expect(pendingBefore.summary.completed).toBe(0);
	expect(pendingBefore.summary.pending).toBe(pendingBefore.summary.totalStaff);

	// Filters narrow the table and can be cleared.
	await page.getByLabel("Search staff").fill("zzz-nobody");
	await expect(page.getByText("No staff match these filters.")).toBeVisible();
	await page.getByRole("button", { name: "Clear filters" }).first().click();
	await expect(staffRow).toBeVisible();

	// Open the appraisal page, rate, and set a goal.
	await staffRow.getByRole("button", { name: /Review appraisal for/ }).click();
	await expect(page).toHaveURL(/\/performance\/staff\/\d+\?cycle=\d+/);
	await expect(page.getByRole("heading", { level: 1 })).not.toHaveText("Appraisal");
	await expect(page.getByText("No ratings have been recorded for this cycle yet.")).toBeVisible();
	await expect(page.getByText("No goals have been set for")).toBeVisible();
	await page.locator("label", { hasText: "4 Stars" }).click();
	await page.getByLabel("Comments").fill("Great with parents");
	await page.getByRole("button", { name: "Save rating" }).click();
	await expect(page.getByText("Great with parents").first()).toBeVisible();
	await expect(page.getByRole("button", { name: "Update rating" })).toBeDisabled(); // nothing changed, so no duplicate save
	await expect(page.getByText("Pending: close the cycle")).toBeVisible();

	await page.getByRole("button", { name: "Add goal" }).first().click();
	const goalDialog = page.getByRole("dialog");
	await goalDialog.getByRole("textbox", { name: /^Goal/ }).fill("Run a remedial class");
	await goalDialog.getByRole("button", { name: "Add goal" }).click();
	await expect(page.getByText("Run a remedial class")).toBeVisible();
	await page.getByRole("combobox", { name: "Status of Run a remedial class" }).click();
	await page.getByRole("option", { name: "Achieved", exact: true }).click();
	await expect(page.getByRole("combobox", { name: "Status of Run a remedial class" })).toHaveText("Achieved");

	// Back on the dashboard the teacher is reviewed, and insights reflect the rating and the goal.
	await page.getByRole("link", { name: "Back to Performance" }).click();
	await expect(staffRow.getByText("Reviewed", { exact: true })).toBeVisible();
	await page.getByLabel("Review status").click();
	await page.getByRole("option", { name: "Pending" }).click();
	await expect(page.getByText("No staff match these filters.")).toBeVisible();
	await page.getByRole("button", { name: "Clear filters" }).first().click();
	await page.getByRole("tab", { name: "Insights" }).click();
	await expect(page.getByText("1 of 1 goals achieved.")).toBeVisible();
	await expect(page.getByRole("img", { name: /Manager ratings: .* 1 rated 4/ })).toBeVisible();

	// Turn self-view on: now the teacher can see their own scorecard and cycle.
	await api.put("/api/v1/performance/settings", { teacherSelfView: true }, owner.token);
	const scorecard = await api.get<{ metrics: unknown[] }>(`/api/v1/performance/me/scorecard?from=${isoDaysAgo(30)}&to=${isoDaysAgo(0)}`, teacherToken);
	expect(scorecard.metrics.length).toBeGreaterThan(0);

	const cycles = await api.get<{ id: number; name: string }[]>("/api/v1/performance/me/cycles", teacherToken);
	const cycle = cycles.find((c) => c.name === cycleName)!;
	expect(cycle).toBeTruthy();

	type Mine = { feedback: { source: string; comment: string; reviewerName: string | null }[]; goals: { title: string }[] };
	// While the cycle is open the manager's rating is hidden from the teacher; the goal is theirs to see.
	const whileOpen = await api.get<Mine>(`/api/v1/performance/me/cycles/${cycle.id}`, teacherToken);
	expect(whileOpen.feedback).toHaveLength(0);
	expect(whileOpen.goals.map((g) => g.title)).toContain("Run a remedial class");

	// The teacher writes a self-appraisal and a goal of their own.
	await api.post(`/api/v1/performance/me/cycles/${cycle.id}/self-appraisal`, { rating: 4, comment: "Steady term" }, teacherToken);
	await api.post(`/api/v1/performance/me/cycles/${cycle.id}/goals`, { title: "Improve homework turnaround", description: "" }, teacherToken);

	// A teacher cannot rate through the admin endpoint, and a closed cycle is read-only.
	expect(
		await statusOf(api.post(`/api/v1/performance/cycles/${cycle.id}/teachers/${seed.teacherPersonId}/feedback`, { source: "MANAGER", rating: 5 }, teacherToken)),
	).toBe(403);
	// Closing is confirmed in the UI and finalizes the reviewed teacher.
	await page.goto(`/performance?cycle=${cycle.id}`);
	await page.getByRole("tab", { name: "Appraisal cycles" }).click();
	await page.getByRole("row", { name: new RegExp(cycleName) }).getByRole("button", { name: "Close cycle" }).click();
	await page.getByRole("dialog").getByRole("button", { name: "Close cycle" }).click();
	await expect(page.getByRole("row", { name: new RegExp(cycleName) }).getByText("Closed")).toBeVisible();
	await page.getByRole("tab", { name: "Overview" }).click();
	await expect(page.getByRole("table", { name: "Staff performance" }).locator("tbody tr").first().getByText("Finalized", { exact: true })).toBeVisible();
	expect(
		await statusOf(api.post(`/api/v1/performance/me/cycles/${cycle.id}/self-appraisal`, { rating: 5, comment: "late" }, teacherToken)),
	).toBe(400);

	// Once closed, the teacher sees the manager's rating (named) alongside their own.
	const afterClose = await api.get<Mine>(`/api/v1/performance/me/cycles/${cycle.id}`, teacherToken);
	expect(afterClose.feedback.map((f) => f.source).sort()).toEqual(["MANAGER", "SELF"]);
	expect(afterClose.feedback.find((f) => f.source === "MANAGER")!.comment).toBe("Great with parents");

	// Restore defaults so a re-run starts clean.
	await api.put("/api/v1/performance/settings", { teacherSelfView: false }, owner.token);
	await api.put("/api/v1/performance/metrics/STAFF_PRESENCE", { enabled: false, weight: 1, target: 100 }, owner.token);
	await api.put(`/api/v1/platform/organisations/${owner.organisationId}/modules/PERFORMANCE`, { enabled: false }, platform.token);
});
