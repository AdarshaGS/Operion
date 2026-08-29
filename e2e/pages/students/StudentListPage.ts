import type { Page } from "@playwright/test";

export class StudentListPage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/students");
	}

	async admitStudent() {
		await this.page.getByRole("button", { name: "Admit student" }).click();
	}

	row(admissionNumber: string) {
		return this.page.getByRole("row").filter({ hasText: admissionNumber });
	}

	async openStudent(admissionNumber: string) {
		await this.row(admissionNumber).click();
	}
}
