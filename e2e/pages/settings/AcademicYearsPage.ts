import type { Page } from "@playwright/test";

export class AcademicYearsPage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/settings/academic-years");
	}

	async addAcademicYear(input: { name: string; startDate: string; endDate: string }) {
		await this.page.getByRole("button", { name: "Add academic year" }).click();
		await this.page.getByLabel("Name").fill(input.name);
		await this.page.getByLabel("Start date").fill(input.startDate);
		await this.page.getByLabel("End date").fill(input.endDate);
		await this.page.getByRole("button", { name: "Add", exact: true }).click();
	}

	yearRow(name: string) {
		return this.page.getByRole("row").filter({ hasText: name });
	}
}
