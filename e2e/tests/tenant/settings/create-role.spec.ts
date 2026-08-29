import { expect, test } from "@playwright/test";
import { RolesPage } from "../../../pages/settings/RolesPage";

test("creating a custom role with a specific permission through the real UI", async ({ page }) => {
	const name = `UI Role ${Date.now()}`;
	const roles = new RolesPage(page);
	await roles.open();

	await roles.addRole({ name, description: "UI-created role", module: "library", permissionCode: "LIBRARY_VIEW" });

	await expect(roles.roleRow(name)).toBeVisible();
	await expect(roles.roleRow(name)).toContainText("1"); // one permission granted
	await expect(roles.roleRow(name)).toContainText("ACTIVE");
});
