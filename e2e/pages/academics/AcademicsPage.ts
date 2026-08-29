import type { Page } from "@playwright/test";

export class AcademicsPage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/academics");
	}

	async addGradeLevel(input: { name: string; sequenceOrder: number; stage?: string }) {
		await this.page.getByRole("button", { name: "Add grade level" }).click();
		await this.page.getByLabel("Name").fill(input.name);
		await this.page.getByLabel("Sequence order").fill(String(input.sequenceOrder));
		if (input.stage) await this.page.getByLabel("Stage").fill(input.stage);
		await this.page.getByRole("button", { name: "Add", exact: true }).click();
	}

	gradeLevelRow(name: string) {
		return this.page.getByRole("table").nth(0).getByRole("row").filter({ hasText: name });
	}

	async addClass(input: { academicYearName: string; campusName: string; gradeLevelName: string; displayName?: string }) {
		await this.page.getByRole("button", { name: "Add class" }).click();
		await this.page.getByRole("combobox", { name: "Academic year" }).click();
		await this.page.getByRole("option", { name: input.academicYearName }).click();
		await this.page.getByRole("combobox", { name: "Campus" }).click();
		await this.page.getByRole("option", { name: input.campusName }).click();
		await this.page.getByRole("combobox", { name: "Grade level" }).click();
		await this.page.getByRole("option", { name: input.gradeLevelName }).click();
		if (input.displayName) await this.page.getByLabel("Display name").fill(input.displayName);
		await this.page.getByRole("button", { name: "Add", exact: true }).click();
	}

	// AcademicsPage.tsx renders GradeLevelsPanel, then SubjectsPanel, then
	// SchoolClassesPanel (see AcademicsPage.tsx) - the 3rd <table> on the page is Classes.
	classRow(name: string) {
		return this.page.getByRole("table").nth(2).getByRole("row").filter({ hasText: name });
	}

	async openClass(name: string) {
		await this.classRow(name).click();
	}

	async addSubject(input: { name: string; code: string; elective?: boolean }) {
		await this.page.getByRole("button", { name: "Add subject" }).click();
		await this.page.getByLabel("Name").fill(input.name);
		await this.page.getByLabel("Code").fill(input.code);
		if (input.elective) await this.page.getByRole("checkbox", { name: "Elective" }).check();
		await this.page.getByRole("button", { name: "Add", exact: true }).click();
	}

	subjectRow(name: string) {
		return this.page.getByRole("table").nth(1).getByRole("row").filter({ hasText: name });
	}

	async toggleSubjectStatus(name: string) {
		await this.subjectRow(name).getByRole("button").click();
	}
}
