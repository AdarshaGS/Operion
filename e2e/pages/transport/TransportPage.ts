import type { Page } from "@playwright/test";

export class TransportPage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/transport");
	}

	async addRoute(input: { campusName: string; name: string; code: string }) {
		await this.page.getByRole("button", { name: "Add route" }).click();
		await this.page.getByRole("combobox", { name: "Campus" }).click();
		await this.page.getByRole("option", { name: input.campusName }).click();
		await this.page.getByLabel("Name").fill(input.name);
		await this.page.getByLabel("Code").fill(input.code);
		await this.page.getByRole("button", { name: "Add", exact: true }).click();
	}

	routeRow(name: string) {
		return this.page.getByRole("row").filter({ hasText: name });
	}

	async openRoute(name: string) {
		await this.routeRow(name).click();
	}
}
