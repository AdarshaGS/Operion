import type { Page } from "@playwright/test";

export class LibraryPage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/library");
	}

	async addBook(input: { title: string; author?: string; isbn?: string; category?: string }) {
		await this.page.getByRole("button", { name: "Add book" }).click();
		await this.page.getByLabel("Title").fill(input.title);
		if (input.author) await this.page.getByLabel("Author").fill(input.author);
		if (input.isbn) await this.page.getByLabel("ISBN").fill(input.isbn);
		if (input.category) await this.page.getByLabel("Category").fill(input.category);
		await this.page.getByRole("button", { name: "Add", exact: true }).click();
	}

	bookRow(title: string) {
		return this.page.getByRole("row").filter({ hasText: title });
	}

	async openBook(title: string) {
		await this.bookRow(title).click();
	}
}
