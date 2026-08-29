import type { Page } from "@playwright/test";

export class StaffCreatePage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/hr/staff/new");
	}

	// Creating the HR extension also requires an employee code + joining date (reused from
	// the base "member" fields) - see StaffCreatePage.tsx's handleSubmit validation.
	async fillMemberDetails(input: { firstName: string; lastName: string; email?: string; employeeCode: string; joiningDate: string }) {
		await this.page.getByLabel("First name").fill(input.firstName);
		await this.page.getByLabel("Last name").fill(input.lastName);
		if (input.email) await this.page.getByLabel("Email").fill(input.email);
		await this.page.getByLabel("Member ID (optional)").fill(input.employeeCode);
		await this.page.getByLabel("Joining date (optional)").fill(input.joiningDate);
	}

	async fillHrExtension(input: { designationName: string }) {
		await this.page.getByRole("combobox", { name: "Designation" }).click();
		await this.page.getByRole("option", { name: input.designationName }).click();
	}

	async submit() {
		await this.page.getByRole("button", { name: "Add staff member" }).click();
	}
}
