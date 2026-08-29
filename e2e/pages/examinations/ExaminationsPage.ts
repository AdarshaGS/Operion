import type { Page } from "@playwright/test";

export class ExaminationsPage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/examinations");
	}

	async selectAcademicYear(academicYearName: string) {
		await this.page.getByRole("combobox", { name: "Academic year" }).click();
		await this.page.getByRole("option", { name: academicYearName }).click();
	}

	async addExam(input: { name: string; examType?: "UNIT_TEST" | "MID_TERM" | "FINAL" | "OTHER" }) {
		await this.page.getByRole("button", { name: "Add exam" }).click();
		await this.page.getByLabel("Name").fill(input.name);
		if (input.examType) {
			await this.page.getByRole("combobox", { name: "Type" }).click();
			await this.page.getByRole("option", { name: input.examType, exact: true }).click();
		}
		await this.page.getByRole("button", { name: "Add", exact: true }).click();
	}

	examRow(name: string) {
		return this.page.getByRole("row").filter({ hasText: name });
	}
}
