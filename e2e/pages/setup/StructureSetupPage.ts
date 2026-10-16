import type { Page } from "@playwright/test";

/** Structure Setup wizard: Organisation profile -> Business settings -> Academic year ->
 * Campuses / locations (optional) -> Review and finish. Linear - a step's tab is disabled
 * until every required step before it is complete, and Save/Continue advance. */
export class StructureSetupPage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/setup/structure");
	}

	async goToStep(label: "Organisation profile" | "Business settings" | "Academic year" | "Campuses / locations") {
		// MUI's Stepper/StepButton exposes these as role="tab", not "button".
		await this.page.getByRole("tab", { name: new RegExp(label) }).click();
	}

	stepTab(label: string) {
		return this.page.getByRole("tab", { name: new RegExp(label) });
	}

	async goToReview() {
		await this.page.getByRole("tab", { name: "Review and finish" }).click();
	}

	/** Contact name + email are what the wizard (and DashboardController.isStructureConfigured)
	 * require; saving advances to Business settings. */
	async fillOrganisationProfile(input: { contactName: string; contactEmail: string }) {
		await this.page.getByLabel("Contact name").fill(input.contactName);
		await this.page.getByLabel("Contact email").fill(input.contactEmail);
		await this.page.getByRole("button", { name: "Save" }).click();
	}

	/** Timezone defaults at org creation - saving as-is confirms it and advances. */
	async saveBusinessSettings() {
		await this.page.getByRole("button", { name: "Save" }).click();
	}

	async addAcademicYear(input: { name: string; startDate: string; endDate: string }) {
		await this.page.getByRole("button", { name: "Add academic year" }).click();
		await this.page.getByLabel("Name").fill(input.name);
		await this.page.getByLabel("Start date").fill(input.startDate);
		await this.page.getByLabel("End date").fill(input.endDate);
		await this.page.getByRole("button", { name: "Add", exact: true }).click();
	}

	async markYearCurrent(name: string) {
		await this.yearRow(name).getByRole("button", { name: "Mark current" }).click();
	}

	yearRow(name: string) {
		return this.page.getByRole("row").filter({ hasText: name });
	}

	async continue() {
		await this.page.getByRole("button", { name: "Continue" }).click();
	}

	finishButton() {
		return this.page.getByRole("button", { name: "Finish setup" });
	}

	async finishSetup() {
		await this.finishButton().click();
	}
}
