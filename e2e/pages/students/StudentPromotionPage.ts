import type { Page } from "@playwright/test";

/** Year-end promotion wizard (web/src/modules/students/StudentPromotionPage.tsx): Years -> Class plan -> Students & apply.
 * The wizard's selects are MUI `<TextField select>` (role "combobox"); the stepper's StepButtons expose role "tab". */
export class StudentPromotionPage {
	constructor(private readonly page: Page) {}

	async open() {
		await this.page.goto("/students/promotion");
	}

	stepTab(name: "Years" | "Class plan" | "Students & apply") {
		return this.page.getByRole("tab", { name });
	}

	// --- Step 1: Years ---

	fromYear() {
		return this.page.getByRole("combobox", { name: "From year" });
	}

	toYear() {
		return this.page.getByRole("combobox", { name: "To year" });
	}

	async selectFromYear(name: string) {
		await this.fromYear().click();
		await this.page.getByRole("option", { name }).click();
	}

	async selectToYear(name: string) {
		await this.toYear().click();
		await this.page.getByRole("option", { name }).click();
	}

	async setEffectiveDate(isoDate: string) {
		await this.page.getByLabel("Effective date").fill(isoDate);
	}

	async preview() {
		await this.page.getByRole("button", { name: "Preview" }).click();
	}

	// --- Step 2: Class plan ---

	classRow(classLabel: string) {
		return this.page.getByRole("row").filter({ hasText: classLabel });
	}

	classTarget(classLabel: string) {
		return this.page.getByRole("combobox", { name: `Target class for ${classLabel}` });
	}

	async chooseClassTarget(classLabel: string, targetLabel: string) {
		await this.classTarget(classLabel).click();
		await this.page.getByRole("option", { name: targetLabel }).click();
	}

	async continueToStudents() {
		await this.page.getByRole("button", { name: "Continue to students" }).click();
	}

	// --- Step 3: Students & apply ---

	studentRow(studentName: string) {
		return this.page.getByRole("row").filter({ hasText: studentName });
	}

	decision(studentName: string) {
		return this.page.getByRole("combobox", { name: `Decision for ${studentName}` });
	}

	targetSection(studentName: string) {
		return this.page.getByRole("combobox", { name: `Target section for ${studentName}` });
	}

	async chooseDecision(studentName: string, option: "Promote" | "Repeat" | "Graduate" | "Skip") {
		await this.decision(studentName).click();
		await this.page.getByRole("option", { name: option, exact: true }).click();
	}

	async chooseTargetSection(studentName: string, sectionOption: string) {
		await this.targetSection(studentName).click();
		await this.page.getByRole("option", { name: sectionOption }).click();
	}

	applyButton() {
		return this.page.getByRole("button", { name: "Apply", exact: true });
	}

	async apply() {
		await this.applyButton().click();
	}

	async retryFailed() {
		await this.page.getByRole("button", { name: "Retry failed" }).click();
	}

	/** "<n> succeeded, <n> skipped (already applied), <n> failed." */
	resultsSummary() {
		return this.page.getByText(/\d+ succeeded, \d+ skipped \(already applied\), \d+ failed\./);
	}
}
