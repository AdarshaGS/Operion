import type { Page } from "@playwright/test";

export class PlatformLoginPage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/platform/login");
	}

	async login(email: string, password: string) {
		await this.page.getByLabel("Email").fill(email);
		await this.page.getByLabel("Password").fill(password);
		await this.page.getByRole("button", { name: "Sign in" }).click();
	}

	errorAlert() {
		return this.page.getByRole("alert");
	}
}
