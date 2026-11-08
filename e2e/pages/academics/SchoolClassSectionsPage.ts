import type { Page } from "@playwright/test";

export class SchoolClassSectionsPage {
	constructor(private readonly page: Page) {}

	async addSection(input: { name: string; capacity?: number; room?: string }) {
		await this.page.getByRole("button", { name: "Add section" }).click();
		await this.page.getByLabel("Name").fill(input.name);
		if (input.capacity) await this.page.getByLabel("Capacity").fill(String(input.capacity));
		if (input.room) await this.page.getByLabel("Room").fill(input.room);
		await this.page.getByRole("button", { name: "Add", exact: true }).click();
	}

	/** Maps an existing subject onto this class (ClassSubjectsPanel, rendered on the same page). */
	async assignSubject(input: { subjectName: string; weeklyPeriods?: number }) {
		await this.page.getByRole("button", { name: "Assign subject" }).click();
		await this.page.getByRole("combobox", { name: "Subject" }).click();
		await this.page.getByRole("option", { name: input.subjectName }).click();
		if (input.weeklyPeriods) await this.page.getByLabel("Periods per week").fill(String(input.weeklyPeriods));
		await this.page.getByRole("button", { name: "Assign", exact: true }).click();
	}

	classSubjectRow(subjectName: string) {
		return this.page.getByRole("row").filter({ has: this.page.getByRole("cell", { name: subjectName, exact: true }) });
	}

	sectionRow(name: string) {
		// hasText does a case-insensitive substring match, and a short section name like "A"
		// is a substring of header cells too ("Capacity", "Name") - filtering by an exact-name
		// cell avoids that, and avoids relying on inter-cell whitespace in the row's
		// concatenated text (which a \b-anchored regex would need but isn't guaranteed).
		return this.page.getByRole("row").filter({ has: this.page.getByRole("cell", { name, exact: true }) });
	}
}
