import type { Page } from "@playwright/test";

export class ItemDetailPage {
	constructor(private readonly page: Page) {}

	async selectCampus(campusName: string) {
		await this.page.getByRole("combobox", { name: "Campus" }).click();
		await this.page.getByRole("option", { name: campusName }).click();
	}

	async recordStockEntry(input: { quantity: number; unitCost?: number; entryDate: string }) {
		await this.page.getByRole("button", { name: "Record entry" }).click();
		await this.page.getByLabel("Quantity").fill(String(input.quantity));
		if (input.unitCost) await this.page.getByLabel("Unit cost").fill(String(input.unitCost));
		await this.page.getByLabel("Entry date").fill(input.entryDate);
		await this.page.getByRole("button", { name: "Record" }).click();
	}

	ledgerRow(source: string) {
		return this.page.getByRole("row").filter({ hasText: source });
	}
}
