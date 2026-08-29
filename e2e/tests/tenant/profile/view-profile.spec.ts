import { readFileSync } from "node:fs";
import { join } from "node:path";
import { expect, test } from "@playwright/test";
import { ProfilePage } from "../../../pages/profile/ProfilePage";
import { AUTH_DIR } from "../../../env";
import type { SeedData } from "../../../global-setup";

const seed: SeedData = JSON.parse(readFileSync(join(AUTH_DIR, "seed.json"), "utf-8"));

test("the profile page shows the caller's own identity and active roles", async ({ page }) => {
	const profile = new ProfilePage(page);
	await profile.open();

	await expect(profile.fieldRow("First name")).toContainText("Owner");
	await expect(profile.fieldRow("Organisation")).toContainText(seed.organisationSlug.replace("e2e-", "E2E Org "));
	// The roles table is the 2nd <table> on the page (profile fields table is the 1st).
	await expect(page.getByRole("table").nth(1)).toContainText("Owner");
	await expect(page.getByRole("table").nth(1)).toContainText("Full access");
});
