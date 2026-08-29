import { readFileSync } from "node:fs";
import { join } from "node:path";
import { expect, test } from "@playwright/test";
import { ExaminationsPage } from "../../../pages/examinations/ExaminationsPage";
import { AUTH_DIR } from "../../../env";
import type { SeedData } from "../../../global-setup";

const seed: SeedData = JSON.parse(readFileSync(join(AUTH_DIR, "seed.json"), "utf-8"));

test("creating an exam through the real UI", async ({ page }) => {
	const examName = `UI Mid Term ${Date.now()}`;
	const examinations = new ExaminationsPage(page);
	await examinations.open();
	await examinations.selectAcademicYear(seed.academicYearName);

	await examinations.addExam({ name: examName, examType: "MID_TERM" });
	await expect(examinations.examRow(examName)).toBeVisible();
	await expect(examinations.examRow(examName)).toContainText("MID_TERM");
});
