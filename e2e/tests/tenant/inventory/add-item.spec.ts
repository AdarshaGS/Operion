import { expect, test } from "@playwright/test";
import { InventoryPage } from "../../../pages/inventory/InventoryPage";

test("creating an item category and an item through the real UI end to end", async ({ page }) => {
	const categoryName = `UI Category ${Date.now()}`;
	const itemName = `UI Item ${Date.now()}`;

	const inventory = new InventoryPage(page);
	await inventory.open();

	await inventory.addCategory({ code: `UIC${Date.now() % 100000}`, name: categoryName });
	await expect(inventory.categoryRow(categoryName)).toBeVisible();

	await inventory.addItem({ categoryName, code: `UII${Date.now() % 100000}`, name: itemName, unit: "PCS" });
	await expect(inventory.itemRow(itemName)).toBeVisible();
	await expect(inventory.itemRow(itemName)).toContainText(categoryName);
});
