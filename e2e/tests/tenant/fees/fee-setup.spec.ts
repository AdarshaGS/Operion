import { readFileSync } from "node:fs";
import { join } from "node:path";
import { expect, test } from "@playwright/test";
import { FeesPage } from "../../../pages/fees/FeesPage";
import { AUTH_DIR } from "../../../env";
import type { SeedData } from "../../../global-setup";

const seed: SeedData = JSON.parse(readFileSync(join(AUTH_DIR, "seed.json"), "utf-8"));

test("creating a fee category and a fee structure through the real forms end to end", async ({ page }) => {
	const categoryCode = `UI-${Date.now()}`;
	const fees = new FeesPage(page);
	await fees.open();

	await fees.addCategory({ code: categoryCode, name: "UI Tuition Fee" });
	await expect(fees.categoryRow(categoryCode)).toBeVisible();

	await fees.selectStructureScope(seed.academicYearName, seed.className);
	await fees.addStructure({ categoryName: "UI Tuition Fee", amount: 5000, installmentDueDate: "2026-07-01" });
	await expect(fees.structureRow("UI Tuition Fee")).toBeVisible();
	await expect(fees.structureRow("UI Tuition Fee")).toContainText("5000");
});
