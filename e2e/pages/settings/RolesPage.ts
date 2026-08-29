import type { Page } from "@playwright/test";

export class RolesPage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/settings/roles");
	}

	async addRole(input: { name: string; description: string; module: string; permissionCode: string }) {
		await this.page.getByRole("button", { name: "Add role" }).click();
		await this.page.getByLabel("Name").fill(input.name);
		await this.page.getByLabel("Description").fill(input.description);
		// Permission checkboxes are grouped into per-module accordions, collapsed by
		// default - expand the target module before its checkbox is interactable.
		await this.page.getByRole("button", { name: new RegExp(`^${input.module}`, "i") }).click();
		await this.page.getByRole("checkbox", { name: new RegExp(`^${input.permissionCode} —`) }).check();
		await this.page.getByRole("button", { name: "Add", exact: true }).click();
	}

	roleRow(name: string) {
		return this.page.getByRole("row").filter({ hasText: name });
	}
}
