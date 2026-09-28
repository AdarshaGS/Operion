import { expect, test } from "@playwright/test";
import { StudentListPage } from "../../../pages/students/StudentListPage";
import { StudentDetailPage } from "../../../pages/students/StudentDetailPage";

test("defining a custom field surfaces it on the student form and detail page (#146)", async ({ page }) => {
	await page.goto("/settings/custom-fields");
	await page.getByRole("button", { name: "Add custom field" }).click();
	await page.getByLabel("Label").fill("Transport route");
	await page.getByLabel("Field key").fill("transport_route_e2e");
	await page.getByRole("dialog").getByRole("button", { name: "Add" }).click();
	await expect(page.getByText("transport_route_e2e")).toBeVisible();

	const admissionNumber = `CF-${Date.now()}`;
	const list = new StudentListPage(page);
	await list.open();
	await list.admitStudent();
	await expect(page).toHaveURL(/\/students\/new/);

	await page.getByRole("textbox", { name: "First name", exact: true }).fill("Grace");
	await page.getByRole("textbox", { name: "Last name", exact: true }).fill("Hopper");
	await page.getByLabel("Admission number").fill(admissionNumber);
	await page.getByLabel("Admission date").fill("2026-06-01");
	await page.getByLabel("Transport route").fill("Route 7");

	// Real page also requires academic-year/class/section/enrollment selections
	// (see admit-student.spec.ts) - reuse those exact same locators here.
	await page.getByRole("combobox", { name: "Academic year" }).click();
	await page.getByRole("option").first().click();
	await page.getByRole("combobox", { name: "Class" }).click();
	await page.getByRole("option").first().click();
	await page.getByRole("combobox", { name: "Section" }).click();
	await page.getByRole("option").first().click();

	await page.getByRole("button", { name: "Admit student" }).click();

	const detail = new StudentDetailPage(page);
	await detail.expectStudentIdInUrl();
	await expect(page.getByText("Route 7")).toBeVisible();

	await page.getByRole("button", { name: "Edit" }).click();
	await expect(page.getByRole("dialog").getByLabel("Transport route")).toHaveValue("Route 7");
	await page.getByRole("dialog").getByLabel("Transport route").fill("Route 12");
	await page.getByRole("dialog").getByRole("button", { name: "Save" }).click();

	await expect(page.getByText("Route 12")).toBeVisible();
});
