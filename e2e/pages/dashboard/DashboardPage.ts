import type { Page } from "@playwright/test";

export class DashboardPage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/dashboard");
		await this.page.waitForResponse(
			(response) => response.url().includes("/api/v1/dashboard/summary") && response.request().method() === "GET",
		);
	}

	greeting() {
		// "Good morning/afternoon/evening, <first name>" - see DashboardPage.tsx's greeting().
		return this.page.getByRole("heading", { name: /^Good (morning|afternoon|evening),/ });
	}

	/** null once every SetupProgress step is done - the card unmounts entirely (see
	 * SetupProgress.tsx), so callers checking "fully done" should assert this is hidden. */
	setupProgressCard() {
		return this.page.getByText("Next setup step");
	}

	setupProgressCount() {
		// "N of 6 setup tasks complete"
		return this.page.getByText(/of 6 setup tasks complete/);
	}

	async goToSetupStep(label: string) {
		// SetupProgress renders each step's label as a <p> (role "paragraph") - scoping to
		// that role avoids colliding with a same-named sidebar nav <button> ("Students",
		// "Fees", ...) that getByText(label, {exact:true}) would otherwise also match.
		await this.page.getByRole("paragraph").filter({ hasText: new RegExp(`^${label}$`) }).click();
	}

	quickAction(label: string) {
		return this.page.getByText(label, { exact: true });
	}

	sectionHeading(title: string) {
		return this.page.getByRole("heading", { name: title, exact: true });
	}

	statTile(label: string) {
		return this.page.getByText(label, { exact: true });
	}
}
