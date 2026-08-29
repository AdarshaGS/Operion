import { readFileSync } from "node:fs";
import { join } from "node:path";
import { expect, test } from "@playwright/test";
import { LoginPage } from "../../pages/auth/LoginPage";
import { AUTH_DIR } from "../../env";
import type { SeedData } from "../../global-setup";

const seed: SeedData = JSON.parse(readFileSync(join(AUTH_DIR, "seed.json"), "utf-8"));

test("requesting a password reset always shows the same non-enumerable confirmation", async ({ page }) => {
	const login = new LoginPage(page);
	await login.open();
	await login.goToForgotPassword();

	await page.getByLabel("Organisation slug").fill(seed.organisationSlug);
	await page.getByLabel("Email").fill(seed.ownerEmail);
	await page.getByRole("button", { name: "Send reset link" }).click();

	await expect(page.getByText(/If that account exists, a reset link has been sent/)).toBeVisible();
});

test("requesting a reset for a nonexistent account shows the identical confirmation (non-enumerable)", async ({ page }) => {
	await page.goto("/forgot-password");
	await page.getByLabel("Organisation slug").fill(seed.organisationSlug);
	await page.getByLabel("Email").fill("nobody-here@example.test");
	await page.getByRole("button", { name: "Send reset link" }).click();

	await expect(page.getByText(/If that account exists, a reset link has been sent/)).toBeVisible();
});
