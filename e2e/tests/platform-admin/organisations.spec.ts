import { expect, test } from "@playwright/test";
import { PlatformOrganisationsPage } from "../../pages/platform/OrganisationsPage";

test("provisioning a new organisation through the real platform-admin UI", async ({ page }) => {
	const run = Date.now();
	const slug = `ui-platform-org-${run}`;

	const organisations = new PlatformOrganisationsPage(page);
	await organisations.open();
	await organisations.addOrganisation({
		name: `UI Platform Org ${run}`,
		legalName: `UI Platform Org ${run} Pvt Ltd`,
		slug,
		adminFirstName: "UI",
		adminLastName: "Admin",
		adminEmail: `admin@${slug}.test`,
		adminPassword: "PlatformPassw0rd!123",
	});

	await expect(organisations.organisationRow(slug)).toBeVisible();
	await expect(organisations.organisationRow(slug)).toContainText("TRIAL");

	await organisations.openOrganisation(slug);
	await expect(page).toHaveURL(/\/platform\/organisations\/\d+$/);
});
