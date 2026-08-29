import { expect, test } from "@playwright/test";
import { MembersPage } from "../../../pages/members/MembersPage";

test("inviting a user with a role through the real UI issues a staff invite and lists the member", async ({ page }) => {
	const run = Date.now();
	const lastName = `Member ${run}`;

	const members = new MembersPage(page);
	await members.open();
	await members.inviteUser({ firstName: "UI", lastName, email: `ui-member-${run}@example.test`, roleName: "Owner" });

	await expect(page.getByRole("heading", { name: "Staff invite issued" })).toBeVisible();
	await members.closeInviteDialog();

	await expect(members.memberRow(`UI ${lastName}`)).toBeVisible();
	await expect(members.memberRow(`UI ${lastName}`)).toContainText("Owner");
});
