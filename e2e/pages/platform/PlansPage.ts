import type { Page } from "@playwright/test";

export class PlatformPlansPage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/platform/plans");
	}

	async addPlan(input: { code: string; name: string; pricePerStudentPerYear: number }) {
		await this.page.getByRole("button", { name: "Add plan" }).click();
		await this.page.getByLabel("Code").fill(input.code);
		await this.page.getByLabel("Name").fill(input.name);
		await this.page.getByLabel("Price per student per year").fill(String(input.pricePerStudentPerYear));
		await this.page.getByRole("button", { name: "Add", exact: true }).click();
	}

	planRow(code: string) {
		return this.page.getByRole("row").filter({ hasText: code });
	}
}
