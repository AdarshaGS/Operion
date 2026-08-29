import type { Page } from "@playwright/test";

export class StructureSetupPage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/setup/structure");
	}

	async goToStep(label: "Organisation profile" | "Business settings" | "Campuses / locations" | "Departments" | "Designations") {
		// MUI's Stepper/StepButton exposes these as role="tab", not "button".
		await this.page.getByRole("tab", { name: new RegExp(label) }).click();
	}

	async goToReview() {
		await this.page.getByRole("tab", { name: "Review and finish" }).click();
	}

	/** Contact name + email are the two fields DashboardController.structureConfigured()
	 * actually checks (timezone already defaults on org creation, see
	 * OrganisationConfiguration.java) - this fills the whole panel for realism anyway. */
	async fillOrganisationProfile(input: { contactName: string; contactEmail: string }) {
		await this.page.getByLabel("Contact name").fill(input.contactName);
		await this.page.getByLabel("Contact email").fill(input.contactEmail);
		await this.page.getByRole("button", { name: "Save" }).click();
	}

	async finishSetup() {
		await this.page.getByRole("button", { name: "Finish setup" }).click();
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
