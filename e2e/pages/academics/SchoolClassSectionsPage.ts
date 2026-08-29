import type { Page } from "@playwright/test";

export class SchoolClassSectionsPage {
	constructor(private readonly page: Page) {}

	async addSection(input: { name: string; capacity?: number; room?: string }) {
		await this.page.getByRole("button", { name: "Add section" }).click();
		await this.page.getByLabel("Name").fill(input.name);
		if (input.capacity) await this.page.getByLabel("Capacity").fill(String(input.capacity));
		if (input.room) await this.page.getByLabel("Room").fill(input.room);
		await this.page.getByRole("button", { name: "Add", exact: true }).click();
	}

	sectionRow(name: string) {
		// hasText does a case-insensitive substring match, and a short section name like "A"
		// is a substring of header cells too ("Capacity", "Name") - filtering by an exact-name
		// cell avoids that, and avoids relying on inter-cell whitespace in the row's
		// concatenated text (which a \b-anchored regex would need but isn't guaranteed).
		return this.page.getByRole("row").filter({ has: this.page.getByRole("cell", { name, exact: true }) });
	}
}
