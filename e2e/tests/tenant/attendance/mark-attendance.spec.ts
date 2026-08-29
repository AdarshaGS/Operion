import { readFileSync } from "node:fs";
import { join } from "node:path";
import { expect, test } from "@playwright/test";
import { AttendancePage } from "../../../pages/attendance/AttendancePage";
import { AUTH_DIR } from "../../../env";
import type { SeedData } from "../../../global-setup";

const seed: SeedData = JSON.parse(readFileSync(join(AUTH_DIR, "seed.json"), "utf-8"));

test("marking and submitting a class register end to end through the real UI", async ({ page }) => {
	const attendance = new AttendancePage(page);
	await attendance.open();
	await attendance.loadRegister(seed.className, seed.sectionName, "2026-06-15");

	await expect(attendance.draftRow("E2E Student")).toBeVisible();
	await attendance.setDraftStatus("E2E Student", "LATE");
	await attendance.submitMarks();

	await expect(attendance.registerRow("E2E Student")).toBeVisible();
	await expect(attendance.registerRow("E2E Student")).toContainText("LATE");

	await attendance.submitRegister();
	await expect(page.getByText("SUBMITTED", { exact: true })).toBeVisible();

	await attendance.lockRegister();
	await expect(page.getByText("LOCKED", { exact: true })).toBeVisible();
});
