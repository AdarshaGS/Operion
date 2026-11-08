import type { Page } from "@playwright/test";

/** Structure Setup wizard: Organisation profile -> Business settings -> Campuses / locations
 * (optional) -> Review and finish. Linear - a step's tab is disabled until every required step
 * before it is complete, and Save/Continue advance. Academic years are not part of the wizard;
 * they are managed on /settings/academic-years (see AcademicYearsPage). */
export class StructureSetupPage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/setup/structure");
	}

	async goToStep(label: "Organisation profile" | "Business settings" | "Campuses / locations") {
		// MUI's Stepper/StepButton exposes these as role="tab", not "button".
		await this.page.getByRole("tab", { name: new RegExp(label) }).click();
	}

	stepTab(label: string) {
		return this.page.getByRole("tab", { name: new RegExp(label) });
	}

	async goToReview() {
		await this.page.getByRole("tab", { name: "Review and finish" }).click();
	}

	/** Contact name + email are what the wizard (and DashboardController.isInstitutionProfileConfigured)
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
