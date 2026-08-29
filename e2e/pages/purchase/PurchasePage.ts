import type { Page } from "@playwright/test";

export class PurchasePage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/purchase");
	}

	async newOrder(input: { supplierName: string; campusName: string; expectedDate: string; itemLabel: string; quantity: number; unitCost: number }) {
		await this.page.getByRole("button", { name: "New purchase order" }).click();
		await this.page.getByRole("combobox", { name: "Supplier" }).click();
		await this.page.getByRole("option", { name: input.supplierName }).click();
		await this.page.getByRole("combobox", { name: "Campus" }).click();
		await this.page.getByRole("option", { name: input.campusName }).click();
		await this.page.getByLabel("Expected date").fill(input.expectedDate);
		await this.page.getByRole("combobox", { name: "Item" }).click();
		await this.page.getByRole("option", { name: input.itemLabel }).click();
		await this.page.getByLabel("Quantity").fill(String(input.quantity));
		await this.page.getByLabel("Unit cost").fill(String(input.unitCost));
		await this.page.getByRole("button", { name: "Create" }).click();
	}

	orderRow(supplierName: string) {
		return this.page.getByRole("row").filter({ hasText: supplierName });
	}
}
