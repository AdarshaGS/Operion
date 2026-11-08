import { fireEvent, render, screen, within } from "@testing-library/react";
import type { LearningScope } from "../../api/learning";
import { afterEach, describe, expect, it, vi } from "vitest";

const SCHEDULE = { id: 7, examName: "Term 1", className: "Grade 5", sectionName: "A", subjectId: 1, subjectName: "Maths", examDate: "2026-11-10", maxMarks: 50 };

function row(enrollmentId: number, status: string) {
	return { enrollmentId, studentId: enrollmentId, studentName: `Student ${enrollmentId}`, rollNumber: enrollmentId, sectionName: "A", sheetId: status === "NOT_UPLOADED" ? null : enrollmentId, status, pages: 1, totalMarks: null };
}

vi.mock("../../api/assets", () => ({ uploadAsset: vi.fn() }));
vi.mock("../../api/learning", () => ({
	listAnswerSheetSchedules: vi.fn(async () => [SCHEDULE]),
	getAnswerSheetRoster: vi.fn(async () => [row(1, "NOT_UPLOADED"), row(2, "UPLOADED"), row(3, "MARKED")]),
	getAnswerSheet: vi.fn(async (id: number) => ({
		id, studentName: "Student", subjectName: "Maths", examName: "Term 1", status: "MARKED", totalMarks: null, maxMarks: 50, overallRemarks: null, pages: [], annotations: [],
	})),
	releaseAnswerSheet: vi.fn(),
	saveAnswerSheetMarking: vi.fn(),
	uploadAnswerSheet: vi.fn(),
}));

import { AnswerSheetsPanel } from "./AnswerSheetsPanel";

const base: LearningScope = { manager: false, reviewer: false, sections: [], subjects: [] };
const teacherOfMaths = { ...base, sections: [{ sectionId: 1, classId: 1, className: "Grade 5", sectionName: "A", writableSubjectIds: [1] }] };

afterEach(() => vi.restoreAllMocks());

async function openRoster() {
	render(<AnswerSheetsPanel scope={scope} />);
	fireEvent.mouseDown(await screen.findByRole("combobox"));
	fireEvent.click(await within(await screen.findByRole("listbox")).findByText(/Term 1/));
	await screen.findByText("Student 1");
}

let scope: LearningScope = base;

// Learning endpoints carry no @RequirePermission: AnswerSheetService.requireWrite = manager OR assigned to teach the subject in the section,
// which the server exposes as scope.manager / scope.sections[].writableSubjectIds (LearningScopeService).
describe("AnswerSheetsPanel write controls (scope = requireWrite)", () => {
	it("offers Upload, Mark and Release to a manager", async () => {
		scope = { ...base, manager: true };
		await openRoster();
		expect(screen.getByText("Upload scans")).toBeInTheDocument();
		expect(screen.getByText("Replace scans")).toBeInTheDocument();
		expect(screen.getAllByRole("button", { name: "Mark" })).toHaveLength(2);
		expect(screen.getByRole("button", { name: "Release" })).toBeInTheDocument();
	});

	it("offers them to a teacher assigned to the subject", async () => {
		scope = teacherOfMaths;
		await openRoster();
		expect(screen.getByText("Upload scans")).toBeInTheDocument();
		expect(screen.getAllByRole("button", { name: "Mark" })).toHaveLength(2);
		expect(screen.getByRole("button", { name: "Release" })).toBeInTheDocument();
	});

	it("shows a view-only caller no upload / mark / release, and no Save in the sheet", async () => {
		scope = base;
		await openRoster();
		expect(screen.queryByText("Upload scans")).not.toBeInTheDocument();
		expect(screen.queryByText("Replace scans")).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Mark" })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Release" })).not.toBeInTheDocument();
		fireEvent.click(screen.getAllByRole("button", { name: "View" })[0]);
		expect(await screen.findByRole("button", { name: "Close" })).toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Save marking" })).not.toBeInTheDocument();
	});

	it("hides write controls from a teacher of a different subject", async () => {
		scope = { ...base, sections: [{ sectionId: 1, classId: 1, className: "Grade 5", sectionName: "A", writableSubjectIds: [2] }] };
		await openRoster();
		expect(screen.queryByText("Upload scans")).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Release" })).not.toBeInTheDocument();
	});
});
