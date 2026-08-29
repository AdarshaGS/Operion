import type { Page } from "@playwright/test";

export class PlatformOrganisationDetailPage {
	constructor(private readonly page: Page) {}

	heading() {
		return this.page.getByRole("heading", { level: 1 });
	}

	// Scoped to the header block (org name + status chip + "Change status" button share one
	// container - see OrganisationDetailPage.tsx) rather than a bare page-wide text search:
	// this page is reached by clicking a row out of a potentially large Organisations table
	// (every org this suite - and any other e2e run - has ever provisioned), so an unscoped
	// status-text locator can transiently collide with that table during the navigation.
	statusChip(status: string) {
		return this.heading().locator("..").getByText(status, { exact: true });
	}

	async changeStatus(status: string) {
		await this.page.getByRole("button", { name: "Change status" }).click();
		await this.page.getByRole("combobox", { name: "New status" }).click();
		await this.page.getByRole("option", { name: status, exact: true }).click();
		await this.page.getByRole("button", { name: "Confirm" }).click();
	}

	async startSubscription(input: { planLabel: string; startDate: string }) {
		await this.page.getByRole("button", { name: "Start subscription" }).click();
		await this.page.getByRole("combobox", { name: "Plan" }).click();
		await this.page.getByRole("option", { name: new RegExp(input.planLabel) }).click();
		await this.page.getByLabel("Start date").fill(input.startDate);
		await this.page.getByRole("button", { name: "Start" }).click();
	}

	subscriptionRow(planName: string) {
		return this.page.getByRole("table").nth(0).getByRole("row").filter({ hasText: planName });
	}

	async generateInvoice(input: { periodStart: string; periodEnd: string; dueDate: string }) {
		await this.page.getByRole("button", { name: "Generate invoice" }).click();
		await this.page.getByLabel("Period start").fill(input.periodStart);
		await this.page.getByLabel("Period end").fill(input.periodEnd);
		await this.page.getByLabel("Due date").fill(input.dueDate);
		await this.page.getByRole("button", { name: "Generate" }).click();
	}

	invoiceRow(periodStart: string) {
		return this.page.getByRole("table").nth(1).getByRole("row").filter({ hasText: periodStart });
	}

	async markInvoicePaid(periodStart: string) {
		await this.invoiceRow(periodStart).getByRole("button", { name: "Mark paid" }).click();
	}
}
