import { readFileSync } from "node:fs";
import { join } from "node:path";
import { expect, test } from "@playwright/test";
import { login } from "../../../api/organisations";
import { AUTH_DIR } from "../../../env";
import type { SeedData } from "../../../global-setup";
import { MembersPage } from "../../../pages/members/MembersPage";

const seed: SeedData = JSON.parse(readFileSync(join(AUTH_DIR, "seed.json"), "utf-8"));

test("adding a member with a password through the real UI lists them and lets them sign in with it", async ({ page }) => {
	const run = Date.now();
	const lastName = `Member ${run}`;
	const email = `ui-member-${run}@example.test`;
	const password = "MemberPassw0rd!123";

	const members = new MembersPage(page);
	await members.open();
	await members.addMember({ firstName: "UI", lastName, email, password, roleName: "Owner" });

	// No invite/claim-token dialog any more - the dialog just closes and the member is listed.
	await expect(page.getByRole("heading", { name: "Staff invite issued" })).toHaveCount(0);
	await expect(members.memberRow(`UI ${lastName}`)).toBeVisible();
	await expect(members.memberRow(`UI ${lastName}`)).toContainText("Owner");

	// The credentials the admin set work on the real login endpoint straight away.
	const session = await login(seed.organisationSlug, email, password);
	expect(session.token).toBeTruthy();
});
