import { readFileSync } from "node:fs";
import { join } from "node:path";
import { expect, test } from "@playwright/test";
import { login } from "../../api/organisations";
import { inviteUser } from "../../api/roles";
import { AUTH_DIR } from "../../env";
import type { SeedData } from "../../global-setup";

const seed: SeedData = JSON.parse(readFileSync(join(AUTH_DIR, "seed.json"), "utf-8"));

test("claiming a staff invite through the real UI activates the account and logs in", async ({ page }) => {
	// Issuing the invite is a precondition, not the thing under test - done via API as
	// the owner (mirrors clicking "Add user" in Members without re-driving that whole
	// flow here; tests/tenant/members/invite-user.spec.ts already covers the issuing UI).
	const ownerSession = await login(seed.organisationSlug, seed.ownerEmail, seed.ownerPassword);
	const invite = await inviteUser(ownerSession.token, `claim-ui-${Date.now()}@example.test`);

	await page.goto(`/claim-staff-invite?org=${seed.organisationSlug}&token=${invite.claimToken}`);
	await page.getByLabel("Choose a password").fill("ClaimedPassw0rd!123");
	await page.getByLabel("Confirm password").fill("ClaimedPassw0rd!123");
	await page.getByRole("button", { name: "Set up account" }).click();

	await expect(page).not.toHaveURL(/\/claim-staff-invite/);
	await expect(page).not.toHaveURL(/\/login/);
});
