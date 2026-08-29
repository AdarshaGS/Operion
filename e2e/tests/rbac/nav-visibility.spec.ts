// Data-driven: for every (nav item, role) pair, asserts the sidebar shows exactly what
// Can.tsx/AppLayout's hasAnyPermission would compute from that role's granted codes -
// literal set membership, not "does this role have equivalent backend access". This is
// deliberate: allFunctionsAdmin holds only the ALL_FUNCTIONS bypass code, so the UI
// shows most items disabled even though PermissionInterceptor lets the real API calls
// through - see tests/rbac/api-enforcement.spec.ts for that half of the picture.

import { expect, test } from "@playwright/test";
import { AppLayoutPage, NAV_ITEMS } from "../../pages/AppLayoutPage";
import { ROLE_FIXTURES } from "../../fixtures/roles";

const OWNER_BYPASSES_EVERYTHING = true;

function grantedCodesFor(projectName: string): { bypassesAll: boolean; codes: string[] } {
	if (projectName === "owner") {
		return { bypassesAll: OWNER_BYPASSES_EVERYTHING, codes: [] };
	}
	if (projectName === "allFunctionsAdmin") {
		return { bypassesAll: false, codes: ["ALL_FUNCTIONS"] };
	}
	const fixture = ROLE_FIXTURES.find((f) => f.name === projectName);
	return { bypassesAll: false, codes: fixture?.permissionCodes ?? [] };
}

for (const item of NAV_ITEMS) {
	test(`nav item "${item.label}" reflects the caller's granted permissions`, async ({ page }, testInfo) => {
		const { bypassesAll, codes } = grantedCodesFor(testInfo.project.name);
		const expectedEnabled = bypassesAll || item.requiredPermissions.length === 0 || item.requiredPermissions.some((p) => codes.includes(p));

		const layout = new AppLayoutPage(page);
		await layout.open();

		await expect(layout.navItem(item.label)).toBeVisible();
		expect(await layout.isNavItemEnabled(item.label)).toBe(expectedEnabled);
	});
}
