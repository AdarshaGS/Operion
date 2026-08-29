import type { Page } from "@playwright/test";

export class PlatformOrganisationsPage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/platform/organisations");
	}

	async addOrganisation(input: {
		name: string;
		legalName: string;
		slug: string;
		adminFirstName: string;
		adminLastName: string;
		adminEmail: string;
		adminPassword: string;
	}) {
		await this.page.getByRole("button", { name: "Add organisation" }).click();
		await this.page.getByLabel("School name").fill(input.name);
		await this.page.getByLabel("Legal name").fill(input.legalName);
		await this.page.getByLabel("Slug").fill(input.slug);
		await this.page.getByLabel("Admin first name").fill(input.adminFirstName);
		await this.page.getByLabel("Admin last name").fill(input.adminLastName);
		await this.page.getByLabel("Admin email").fill(input.adminEmail);
		await this.page.getByLabel("Admin password").fill(input.adminPassword);
		await this.page.getByRole("button", { name: "Create" }).click();
	}

	organisationRow(slug: string) {
		return this.page.getByRole("row").filter({ hasText: slug });
	}

	async openOrganisation(slug: string) {
		await this.organisationRow(slug).click();
	}
}
