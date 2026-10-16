import type { Page } from "@playwright/test";

/** Settings > Designations - configured here after onboarding (no longer a Structure Setup step). */
export class DesignationsPage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/settings/designations");
	}

	async addDesignation(name: string) {
		await this.page.getByRole("button", { name: "Add designation" }).click();
		await this.page.getByLabel("Name").fill(name);
		await this.page.getByRole("button", { name: "Add", exact: true }).click();
	}

	designationRow(name: string) {
		return this.page.getByRole("row").filter({ hasText: name });
	}
}
