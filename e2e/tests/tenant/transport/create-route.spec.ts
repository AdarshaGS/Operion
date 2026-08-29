import { expect, test } from "@playwright/test";
import { TransportPage } from "../../../pages/transport/TransportPage";

test("creating a transport route through the real UI", async ({ page }) => {
	const name = `UI Route ${Date.now()}`;
	const transport = new TransportPage(page);
	await transport.open();

	await transport.addRoute({ campusName: "Main Campus", name, code: `R${Date.now() % 100000}` });
	await expect(transport.routeRow(name)).toBeVisible();
	await expect(transport.routeRow(name)).toContainText("Main Campus");
});
