import { expect, test } from "@playwright/test";
import { CommunicationPage } from "../../../pages/communication/CommunicationPage";

test("composing, saving as draft, and publishing an announcement through the real UI", async ({ page }) => {
	const title = `UI Announcement ${Date.now()}`;
	const communication = new CommunicationPage(page);
	await communication.open();

	await communication.composeDraft({ title, body: "This is a UI-driven test announcement." });
	await expect(communication.draftRow(title)).toBeVisible();

	await communication.publish(title);
	await expect(communication.publishedRow(title)).toBeVisible();
});
