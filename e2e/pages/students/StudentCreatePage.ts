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
		await this.page.getByLabel("First name").fill(input.firstName);
		await this.page.getByLabel("Last name").fill(input.lastName);
		if (input.dateOfBirth) await this.page.getByLabel("Date of birth").fill(input.dateOfBirth);
		if (input.gender) await this.page.getByLabel("Gender").fill(input.gender);
		await this.page.getByLabel("Admission number").fill(input.admissionNumber);
		await this.page.getByLabel("Admission date").fill(input.admissionDate);
		if (input.previousSchool) await this.page.getByLabel("Previous school").fill(input.previousSchool);
	}

	async submit() {
		await this.page.getByRole("button", { name: "Admit student" }).click();
	}
}
