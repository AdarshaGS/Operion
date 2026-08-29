import type { Page } from "@playwright/test";

export class InventoryPage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/inventory");
	}

	// InventoryPage renders ItemCategoriesPanel then ItemsPanel (see InventoryPage.tsx),
	// so their tables are reliably the 1st and 2nd <table> - same reasoning as FeesPage.
	private categoriesTable() {
		return this.page.getByRole("table").nth(0);
	}

	private itemsTable() {
		return this.page.getByRole("table").nth(1);
	}

	async addCategory(input: { code: string; name: string }) {
		await this.page.getByRole("button", { name: "Add category" }).click();
		await this.page.getByLabel("Code").fill(input.code);
		await this.page.getByLabel("Name").fill(input.name);
		await this.page.getByRole("button", { name: "Add", exact: true }).click();
	}

	categoryRow(name: string) {
		return this.categoriesTable().getByRole("row").filter({ hasText: name });
	}

	async addItem(input: { categoryName: string; code: string; name: string; unit: string }) {
		await this.page.getByRole("button", { name: "Add item" }).click();
		await this.page.getByRole("combobox", { name: "Category" }).click();
		await this.page.getByRole("option", { name: input.categoryName }).click();
		await this.page.getByLabel("Code").fill(input.code);
		await this.page.getByLabel("Name").fill(input.name);
		await this.page.getByLabel("Unit").fill(input.unit);
		await this.page.getByRole("button", { name: "Add", exact: true }).click();
	}

	itemRow(name: string) {
		return this.itemsTable().getByRole("row").filter({ hasText: name });
	}

	async openItem(name: string) {
		await this.itemRow(name).click();
	}

	async addSupplier(input: { name: string; phone?: string }) {
		await this.page.getByRole("button", { name: "Add supplier" }).click();
		await this.page.getByLabel("Name").fill(input.name);
		if (input.phone) await this.page.getByLabel("Phone").fill(input.phone);
		await this.page.getByRole("button", { name: "Add", exact: true }).click();
	}

	supplierRow(name: string) {
		return this.page.getByRole("row").filter({ hasText: name });
	}

	async addCustomer(input: { name: string; phone?: string }) {
		await this.page.getByRole("button", { name: "Add customer" }).click();
		await this.page.getByLabel("Name").fill(input.name);
		if (input.phone) await this.page.getByLabel("Phone").fill(input.phone);
		await this.page.getByRole("button", { name: "Add", exact: true }).click();
	}

	customerRow(name: string) {
		return this.page.getByRole("row").filter({ hasText: name });
	}
}
