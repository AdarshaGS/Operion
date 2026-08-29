import type { Page } from "@playwright/test";

export class ReportsPage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/reports");
	}

	async seedStandardReports() {
		await this.page.getByRole("button", { name: "Seed standard reports" }).click();
	}

	reportRow(name: string) {
		return this.page.getByRole("row").filter({ hasText: name });
	}

	async openReport(name: string) {
		await this.reportRow(name).click();
	}
}
