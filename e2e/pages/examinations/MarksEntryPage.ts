import type { Page } from "@playwright/test";

export class MarksEntryPage {
	constructor(private readonly page: Page) {}

	async open(examId: number, scheduleId: number) {
		await this.page.goto(`/examinations/exams/${examId}/schedules/${scheduleId}`);
	}

	private row(studentName: string) {
		return this.page.getByRole("row").filter({ hasText: studentName });
	}

	// The marks/remarks TextFields have no accessible <label> (see MarksEntryPage.tsx) -
	// number and text inputs render distinct ARIA roles (spinbutton vs textbox), which is
	// enough to target them precisely within a student's row without any fragile selector.
	async enterMarksFor(studentName: string, marksObtained: number, remarks?: string) {
		const row = this.row(studentName);
		await row.getByRole("spinbutton").fill(String(marksObtained));
		if (remarks) await row.getByRole("textbox").fill(remarks);
	}

	async submitMarks() {
		await this.page.getByRole("button", { name: "Submit marks" }).click();
	}

	savedMarksRow(studentName: string) {
		return this.row(studentName);
	}
}
