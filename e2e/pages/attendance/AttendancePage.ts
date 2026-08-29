import type { Page } from "@playwright/test";

export class AttendancePage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/attendance");
	}

	async loadRegister(className: string, sectionName: string, date: string) {
		await this.page.getByRole("combobox", { name: "Class", exact: true }).click();
		await this.page.getByRole("option", { name: className }).click();
		await this.page.getByRole("combobox", { name: "Section" }).click();
		await this.page.getByRole("option", { name: sectionName, exact: true }).click();
		await this.page.getByLabel("Date").fill(date);
		await this.page.getByRole("button", { name: "Load" }).click();
	}

	draftRow(studentName: string) {
		return this.page.getByRole("row").filter({ hasText: studentName });
	}

	async setDraftStatus(studentName: string, status: "PRESENT" | "ABSENT" | "LATE" | "HALF_DAY") {
		const row = this.draftRow(studentName);
		await row.getByRole("combobox").click();
		await this.page.getByRole("option", { name: status, exact: true }).click();
	}

	async submitMarks() {
		await this.page.getByRole("button", { name: "Submit marks" }).click();
	}

	registerRow(studentName: string) {
		return this.page.getByRole("row").filter({ hasText: studentName });
	}

	async submitRegister() {
		await this.page.getByRole("button", { name: "Submit", exact: true }).click();
	}

	async lockRegister() {
		await this.page.getByRole("button", { name: "Lock", exact: true }).click();
	}
}
