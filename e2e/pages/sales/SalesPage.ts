import type { Page } from "@playwright/test";

export class SalesPage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/sales");
	}

	async newSale(input: { customerLabel: string; campusName: string; saleDate: string; itemLabel: string; quantity: number; unitPrice: number }) {
		await this.page.getByRole("button", { name: "New sale" }).click();
		await this.page.getByRole("combobox", { name: "Customer" }).click();
		await this.page.getByRole("option", { name: input.customerLabel }).click();
		await this.page.getByRole("combobox", { name: "Campus" }).click();
		await this.page.getByRole("option", { name: input.campusName }).click();
		await this.page.getByLabel("Sale date").fill(input.saleDate);
		await this.page.getByRole("combobox", { name: "Item" }).click();
		await this.page.getByRole("option", { name: input.itemLabel }).click();
		await this.page.getByLabel("Quantity").fill(String(input.quantity));
		await this.page.getByLabel("Unit price").fill(String(input.unitPrice));
		await this.page.getByRole("button", { name: "Complete sale" }).click();
	}

	saleRow(customerLabel: string) {
		return this.page.getByRole("row").filter({ hasText: customerLabel });
	}
}
