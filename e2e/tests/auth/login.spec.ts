import { readFileSync } from "node:fs";
import { join } from "node:path";
import { expect, test } from "@playwright/test";
import { LoginPage } from "../../pages/auth/LoginPage";
import { AUTH_DIR } from "../../env";
import type { SeedData } from "../../global-setup";

const seed: SeedData = JSON.parse(readFileSync(join(AUTH_DIR, "seed.json"), "utf-8"));

test("signing in with valid credentials through the real form reaches the app", async ({ page }) => {
	const login = new LoginPage(page);
	await login.open();
	await login.login(seed.organisationSlug, seed.ownerEmail, seed.ownerPassword);

	// Owner holds ORGANISATION_MANAGE (granted every current permission at provisioning -
	// see OrganisationService.seedDefaultRoles), so IndexRedirect sends them to /dashboard.
	await expect(page).toHaveURL(/\/dashboard/);
});

test("signing in with the wrong password shows an error and stays on the login page", async ({ page }) => {
	const login = new LoginPage(page);
	await login.open();
	await login.login(seed.organisationSlug, seed.ownerEmail, "definitely-wrong-password");

	await expect(login.errorAlert()).toBeVisible();
	await expect(page).toHaveURL(/\/login/);
});
