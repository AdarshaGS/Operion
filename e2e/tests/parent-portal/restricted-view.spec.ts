// The app has no purpose-built guardian/parent screens yet (guardian shares the same
// AppLayout and routes as staff - see IndexRedirect and load-context.md's Milestone 3
// notes on the "Guardian" managed role only carrying PARENT_PORTAL_ACCESS). This spec
// documents the current, real boundary: a guardian is NOT shown the student roster or
// any other staff module, because they hold no STUDENT_VIEW (or any other module)
// permission - the backend blocks the underlying API calls, which surfaces as an error
// state on whatever staff screen they land on, not a friendly "nothing here yet" page.

import { expect, test } from "@playwright/test";
import { AppLayoutPage } from "../../pages/AppLayoutPage";

test("a guardian lands on /students (per IndexRedirect) but cannot see the roster - no STUDENT_VIEW", async ({ page }) => {
	await page.goto("/");
	await expect(page).toHaveURL(/\/students/);
	await expect(page.getByRole("alert")).toBeVisible();
});

test("a guardian cannot reach any other staff module either - every nav item but Settings is disabled", async ({ page }) => {
	const layout = new AppLayoutPage(page);
	await layout.open();

	await expect(layout.navItem("Students")).toBeVisible();
	expect(await layout.isNavItemEnabled("Students")).toBe(false);
	expect(await layout.isNavItemEnabled("Fees")).toBe(false);
	expect(await layout.isNavItemEnabled("HR")).toBe(false);
	expect(await layout.isNavItemEnabled("Settings")).toBe(true);
});
