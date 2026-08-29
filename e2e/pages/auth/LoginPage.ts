import type { Page } from "@playwright/test";

export class LoginPage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/login");
	}

	async login(organisationSlug: string, email: string, password: string) {
		await this.page.getByLabel("Organisation slug").fill(organisationSlug);
		await this.page.getByLabel("Email").fill(email);
		await this.page.getByLabel("Password").fill(password);
		await this.page.getByRole("button", { name: "Sign in" }).click();
	}

	errorAlert() {
		return this.page.getByRole("alert");
	}

	async goToForgotPassword() {
		await this.page.getByRole("link", { name: "Forgot password?" }).click();
	}
}
