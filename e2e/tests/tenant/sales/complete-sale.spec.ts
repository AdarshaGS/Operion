import { expect, test } from "@playwright/test";
import { InventoryPage } from "../../../pages/inventory/InventoryPage";
import { ItemDetailPage } from "../../../pages/inventory/ItemDetailPage";
import { SalesPage } from "../../../pages/sales/SalesPage";

test("completing a sale through the real UI, after stocking its item and setting up its customer", async ({ page }) => {
	const run = Date.now();
	const categoryName = `UI Sale Category ${run}`;
	const itemCode = `UISALE${run % 100000}`;
	const itemName = `UI Sale Item ${run}`;
	const customerName = `UI Customer ${run}`;

	const inventory = new InventoryPage(page);
	await inventory.open();
	await inventory.addCategory({ code: `UISC${run % 100000}`, name: categoryName });
	await inventory.addItem({ categoryName, code: itemCode, name: itemName, unit: "PCS" });
	await inventory.addCustomer({ name: customerName, phone: "9991112222" });
	await expect(inventory.customerRow(customerName)).toBeVisible();

	// A sale deducts real stock (SaleService.recordIssue) - a freshly created item starts
	// at zero balance, so it needs a stock entry before it can be sold.
	await inventory.openItem(itemName);
	const itemDetail = new ItemDetailPage(page);
	await itemDetail.selectCampus("Main Campus");
	await itemDetail.recordStockEntry({ quantity: 50, unitCost: 20, entryDate: "2026-07-01" });
	await expect(itemDetail.ledgerRow("ENTRY")).toBeVisible();

	const sales = new SalesPage(page);
	await sales.open();
	await sales.newSale({
		customerLabel: `${customerName} (9991112222)`,
		campusName: "Main Campus",
		saleDate: "2026-07-20",
		itemLabel: `${itemCode} - ${itemName}`,
		quantity: 2,
		unitPrice: 100,
	});

	await expect(sales.saleRow(customerName)).toBeVisible();
	await expect(sales.saleRow(customerName)).toContainText("200");
});
