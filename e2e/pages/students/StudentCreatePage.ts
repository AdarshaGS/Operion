import type { Page } from "@playwright/test";

export interface NewStudentInput {
	firstName: string;
	lastName: string;
	admissionNumber: string;
	admissionDate: string; // YYYY-MM-DD
	dateOfBirth?: string;
	gender?: string;
	previousSchool?: string;
}

export class StudentCreatePage {
	constructor(private readonly page: Page) {}

	async fill(input: NewStudentInput) {
		// exact: true - the real form also has "Guardian first/last name" fields, whose
		// accessible names otherwise substring-match "First/Last name" under Playwright's
		// default (non-exact) getByLabel matching.
		await this.page.getByLabel("First name", { exact: true }).fill(input.firstName);
		await this.page.getByLabel("Last name", { exact: true }).fill(input.lastName);
		if (input.dateOfBirth) await this.page.getByLabel("Date of birth").fill(input.dateOfBirth);
		// Gender is a dropdown (Male / Female / Other), not free text.
		// Mandatory field - defaults to "Male" when a test does not care.
		await this.page.getByRole("combobox", { name: /^Gender/ }).click();
		await this.page.getByRole("option", { name: input.gender ?? "Male", exact: true }).click();
		await this.page.getByLabel("Admission number").fill(input.admissionNumber);
		await this.page.getByLabel("Admission date").fill(input.admissionDate);
		if (input.previousSchool) await this.page.getByLabel("Previous school").fill(input.previousSchool);
	}

	async submit() {
		await this.page.getByRole("button", { name: "Admit student" }).click();
	}
}
