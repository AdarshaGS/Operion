import { expect, test } from "@playwright/test";
import { InventoryPage } from "../../../pages/inventory/InventoryPage";
import { PurchasePage } from "../../../pages/purchase/PurchasePage";

test("creating a purchase order through the real UI, after setting up its supplier and item", async ({ page }) => {
	const run = Date.now();
	const categoryName = `UI PO Category ${run}`;
	const itemCode = `UIPO${run % 100000}`;
	const itemName = `UI PO Item ${run}`;
	const supplierName = `UI Supplier ${run}`;

	const inventory = new InventoryPage(page);
	await inventory.open();
	await inventory.addCategory({ code: `UIPOC${run % 100000}`, name: categoryName });
	await inventory.addItem({ categoryName, code: itemCode, name: itemName, unit: "PCS" });
	await inventory.addSupplier({ name: supplierName, phone: "9998887777" });
	await expect(inventory.supplierRow(supplierName)).toBeVisible();

	const purchase = new PurchasePage(page);
	await purchase.open();
	await purchase.newOrder({
		supplierName,
		campusName: "Main Campus",
		expectedDate: "2026-07-15",
		itemLabel: `${itemCode} - ${itemName}`,
		quantity: 10,
		unitCost: 25,
	});

	await expect(purchase.orderRow(supplierName)).toBeVisible();
	await expect(purchase.orderRow(supplierName)).toContainText("DRAFT");
});
