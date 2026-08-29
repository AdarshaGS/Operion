import type { Page } from "@playwright/test";

export class StudentDetailPage {
	constructor(private readonly page: Page) {}

	async expectStudentIdInUrl() {
		return this.page.waitForURL(/\/students\/\d+$/);
	}

	studentName(fullName: string) {
		return this.page.getByText(fullName, { exact: true });
	}

	admissionNumber(value: string) {
		return this.page.getByText(value, { exact: true });
	}

	statusChip(status: string) {
		return this.page.getByText(status, { exact: true });
	}

	/** Moves Student.status ADMITTED -> ACTIVE (see Student.java) - the only UI path to
	 * that transition, added alongside this suite (StudentDetailPage.tsx's EnrollDialog). */
	async enroll(input: { academicYearName: string; className: string; sectionName: string; rollNumber?: number; enrolledDate: string }) {
		await this.page.getByRole("button", { name: "Enroll student" }).click();
		await this.page.getByRole("combobox", { name: "Academic year" }).click();
		await this.page.getByRole("option", { name: input.academicYearName }).click();
		await this.page.getByRole("combobox", { name: "Class" }).click();
		await this.page.getByRole("option", { name: input.className }).click();
		await this.page.getByRole("combobox", { name: "Section" }).click();
		await this.page.getByRole("option", { name: input.sectionName, exact: true }).click();
		if (input.rollNumber) await this.page.getByLabel("Roll number").fill(String(input.rollNumber));
		await this.page.getByLabel("Enrolled date").fill(input.enrolledDate);
		await this.page.getByRole("button", { name: "Enroll", exact: true }).click();
	}

	enrolledBanner() {
		// Scoped to the success Alert (role="alert") and anchored on "Enrolled -" (with the
		// dash) so this doesn't collide with the enroll dialog's "Enrolled date" field label,
		// which can still be in the DOM mid-close when this is checked.
		return this.page.getByRole("alert").filter({ hasText: "Enrolled -" });
	}
}
