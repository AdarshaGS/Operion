import { expect, test } from "@playwright/test";
import { StudentListPage } from "../../../pages/students/StudentListPage";
import { StudentCreatePage } from "../../../pages/students/StudentCreatePage";
import { StudentDetailPage } from "../../../pages/students/StudentDetailPage";

test("admitting a student through the real form creates it end to end and shows it in the list", async ({ page }) => {
	const admissionNumber = `UI-${Date.now()}`;

	const list = new StudentListPage(page);
	await list.open();
	await list.admitStudent();
	await expect(page).toHaveURL(/\/students\/new/);

	const create = new StudentCreatePage(page);
	await create.fill({
		firstName: "Ada",
		lastName: "Lovelace",
		admissionNumber,
		admissionDate: "2026-06-01",
	});
	await create.submit();

	const detail = new StudentDetailPage(page);
	await detail.expectStudentIdInUrl();
	await expect(detail.studentName("Ada Lovelace")).toBeVisible();
	await expect(detail.admissionNumber(admissionNumber)).toBeVisible();

	await list.open();
	await expect(list.row(admissionNumber)).toBeVisible();
	await expect(list.row(admissionNumber)).toContainText("Ada Lovelace");
});
