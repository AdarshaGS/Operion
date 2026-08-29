import type { Page } from "@playwright/test";

export class CommunicationPage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/communication");
	}

	/** Audience defaults to ORG (org-wide) - no extra fields needed. */
	async composeDraft(input: { title: string; body: string }) {
		await this.page.getByLabel("Title").fill(input.title);
		await this.page.getByLabel("Body").fill(input.body);
		await this.page.getByRole("button", { name: "Save as draft" }).click();
	}

	draftRow(title: string) {
		return this.page.getByRole("row").filter({ hasText: title });
	}

	async publish(title: string) {
		await this.draftRow(title).getByRole("button", { name: "Publish" }).click();
	}

	publishedRow(title: string) {
		return this.page.getByRole("row").filter({ hasText: title });
	}
}
