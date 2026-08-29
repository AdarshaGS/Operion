import type { Page } from "@playwright/test";

export class MembersPage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/members");
	}

	async inviteUser(input: { firstName: string; lastName: string; email: string; roleName: string }) {
		await this.page.getByRole("button", { name: "Add user" }).click();
		await this.page.getByLabel("First name").fill(input.firstName);
		await this.page.getByLabel("Last name").fill(input.lastName);
		await this.page.getByLabel("Email").fill(input.email);
		await this.page.getByRole("combobox", { name: /Role\(s\)/ }).click();
		await this.page.getByRole("option", { name: input.roleName }).click();
		await this.page.keyboard.press("Escape"); // close the multi-select dropdown
		await this.page.getByRole("button", { name: "Invite" }).click();
	}

	async closeInviteDialog() {
		await this.page.getByRole("button", { name: "Close" }).click();
	}

	memberRow(personName: string) {
		return this.page.getByRole("row").filter({ hasText: personName });
	}
}
