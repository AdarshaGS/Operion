import { expect, test } from "@playwright/test";
import { AcademicsPage } from "../../../pages/academics/AcademicsPage";

test("creating a subject and toggling its status through the real UI", async ({ page }) => {
	const name = `UI Subject ${Date.now()}`;
	const academics = new AcademicsPage(page);
	await academics.open();

	await academics.addSubject({ name, code: `UIS${Date.now() % 100000}`, elective: true });
	await expect(academics.subjectRow(name)).toBeVisible();
	await expect(academics.subjectRow(name)).toContainText("ACTIVE");

	await academics.toggleSubjectStatus(name);
	await expect(academics.subjectRow(name)).toContainText("INACTIVE");
});
