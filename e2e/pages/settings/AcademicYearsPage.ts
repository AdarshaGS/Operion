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

	async markCurrent(name: string) {
		await this.yearRow(name).getByRole("button", { name: "Mark current" }).click();
	}

	/** The back arrow reads "Back to dashboard" when the page was opened from the dashboard setup card,
	 * "Back to settings" otherwise (see SettingsSectionPage.tsx / useBackTarget). */
	async backToDashboard() {
		await this.page.getByRole("button", { name: "Back to dashboard" }).click();
	}

	yearRow(name: string) {
		return this.page.getByRole("row").filter({ hasText: name });
	}
}
