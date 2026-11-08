import type { Page } from "@playwright/test";

export class MembersPage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/members");
	}

	/** Creates a member with an admin-set password (no invite/claim step): the member can sign
	 * in with these credentials as soon as the form is submitted. "Add member" opens a full page
	 * (/members/new), which returns to the members list on success. */
	async addMember(input: { firstName: string; lastName: string; email: string; password: string; roleName: string }) {
		await this.page.getByRole("button", { name: "Add member" }).click();
		await this.page.waitForURL(/\/members\/new$/);
		await this.page.getByLabel("First name").fill(input.firstName);
		await this.page.getByLabel("Last name").fill(input.lastName);
		// Gender is mandatory on the form and has no default.
		await this.page.getByRole("combobox", { name: /^Gender/ }).click();
		await this.page.getByRole("option", { name: "Male", exact: true }).click();
		await this.page.getByLabel("Email").fill(input.email);
		await this.page.getByLabel("Password", { exact: true }).fill(input.password);
		await this.page.getByRole("combobox", { name: /Role\(s\)/ }).click();
		await this.page.getByRole("option", { name: input.roleName }).click();
		await this.page.keyboard.press("Escape"); // close the multi-select dropdown
		await this.page.getByRole("button", { name: "Add member" }).click(); // submit on the page
		await this.page.waitForURL(/\/members$/);
	}

	memberRow(personName: string) {
		return this.page.getByRole("row").filter({ hasText: personName });
	}
}
