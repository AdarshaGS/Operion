import { expect, test } from "@playwright/test";
import { LibraryPage } from "../../../pages/library/LibraryPage";

test("adding a book to the catalog through the real UI", async ({ page }) => {
	const title = `UI Book ${Date.now()}`;
	const library = new LibraryPage(page);
	await library.open();

	await library.addBook({ title, author: "Ada Lovelace", category: "Fiction" });
	await expect(library.bookRow(title)).toBeVisible();
	await expect(library.bookRow(title)).toContainText("Ada Lovelace");
});
