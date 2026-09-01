import type { Page } from "@playwright/test";

export class FeesPage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/fees");
	}

	async addCategory(input: { code: string; name: string; description?: string }) {
		await this.page.getByRole("button", { name: "Add category" }).click();
		await this.page.getByLabel("Code").fill(input.code);
		await this.page.getByLabel("Name").fill(input.name);
		if (input.description) await this.page.getByLabel("Description").fill(input.description);
		await this.page.getByRole("button", { name: "Add", exact: true }).click();
	}

	// FeesPage renders FeeCategoriesPanel then FeeStructuresPanel (see FeesPage.tsx), so
	// their tables are reliably the 1st and 2nd <table> on the page - scoping by table
	// index avoids the two panels' rows colliding when a category/structure share text
	// (e.g. the same fee category name shows in both).
	private categoriesTable() {
		return this.page.getByRole("table").nth(0);
	}

	private structuresTable() {
		return this.page.getByRole("table").nth(1);
	}

	categoryRow(code: string) {
		return this.categoriesTable().getByRole("row").filter({ hasText: code });
	}

	async selectStructureScope(academicYearName: string, className: string) {
		await this.page.getByRole("combobox", { name: "Academic year" }).click();
		await this.page.getByRole("option", { name: academicYearName }).click();
		await this.page.getByRole("combobox", { name: "Class", exact: true }).click();
		await this.page.getByRole("option", { name: className }).click();
	}

	/** Only needed the first time a class/year has no fee structure group yet. */
	async setUpStructure(name: string) {
		await this.page.getByRole("button", { name: "Set up" }).click();
		await this.page.getByLabel("Name").fill(name);
		await this.page.getByRole("button", { name: "Create" }).click();
	}

	async addStructure(input: { categoryName: string; amount: number; installmentDueDate: string }) {
		await this.page.getByRole("button", { name: "Add component" }).click();
		await this.page.getByRole("combobox", { name: "Fee category" }).click();
		await this.page.getByRole("option", { name: input.categoryName }).click();
		await this.page.getByLabel("Total amount").fill(String(input.amount));
		await this.page.getByLabel("Due date").fill(input.installmentDueDate);
		await this.page.getByLabel("Amount", { exact: true }).fill(String(input.amount));
		await this.page.getByRole("button", { name: "Add", exact: true }).click();
	}

	structureRow(categoryName: string) {
		return this.structuresTable().getByRole("row").filter({ hasText: categoryName });
	}
}
