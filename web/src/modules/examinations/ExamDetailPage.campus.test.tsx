import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { setSelectedCampusId } from "../../layout/selectedCampus";

const CLASSES = [
	{ id: 1, academicYearId: 1, campusId: 1, campusName: "Main Campus", gradeLevelId: 1, displayName: "LKG Main", status: "ACTIVE" },
	{ id: 2, academicYearId: 1, campusId: 2, campusName: "Branch", gradeLevelId: 1, displayName: "LKG Branch", status: "ACTIVE" },
];

vi.mock("../../api/exams", () => ({
	getExam: vi.fn(async () => ({ id: 5, academicYearId: 1, name: "Term 1", examType: "TERM", status: "DRAFT" })),
	// The exam's only schedule belongs to the Branch campus class.
	listSchedules: vi.fn(async () => [
		{ id: 50, examId: 5, schoolClassId: 2, sectionId: null, subjectId: 1, examDate: "2026-03-01", maxMarks: 100, passMarks: 35 },
	]),
	addSchedule: vi.fn(),
}));
vi.mock("../../api/schoolClasses", () => ({ listSchoolClasses: vi.fn(async () => CLASSES) }));
vi.mock("../../api/sections", () => ({ listSections: vi.fn(async () => []) }));
vi.mock("../../api/subjects", () => ({ listSubjects: vi.fn(async () => [{ id: 1, name: "Maths", code: "MA", status: "ACTIVE" }]) }));
vi.mock("../../api/marks", () => ({ getRegister: vi.fn(async () => ({ id: null })) }));
vi.mock("../../api/enrollments", () => ({ listStudentEnrollments: vi.fn(async () => []) }));
vi.mock("../../api/gradingScales", () => ({ listGradingScales: vi.fn(async () => []) }));
vi.mock("../../api/persons", () => ({ listPersons: vi.fn(async () => []) }));
vi.mock("../../api/students", () => ({ listStudents: vi.fn(async () => []) }));
vi.mock("../../api/reportCards", () => ({
	downloadReportCardPdf: vi.fn(),
	downloadSectionReportCardsPdf: vi.fn(),
	publishReportCard: vi.fn(),
}));
vi.mock("../../auth/AuthContext", () => ({ useAuth: () => ({ hasPermission: () => true }) }));
vi.mock("../../utils/useSchoolClassLabel", () => ({
	useSchoolClassLabel: () => (c: { displayName: string | null }) => c.displayName ?? "Class",
}));

import { ExamDetailPage } from "./ExamDetailPage";

beforeEach(() => {
	act(() => setSelectedCampusId(null));
});

function renderPage() {
	return render(
		<MemoryRouter initialEntries={["/examinations/exams/5"]}>
			<Routes>
				<Route path="/examinations/exams/:examId" element={<ExamDetailPage />} />
			</Routes>
		</MemoryRouter>,
	);
}

async function openAddSchedule() {
	fireEvent.click(await screen.findByRole("tab", { name: "Schedule" }));
	fireEvent.click(await screen.findByRole("button", { name: "Add schedule" }));
}

async function dialogClassOptions(): Promise<string[]> {
	fireEvent.mouseDown(await screen.findByRole("combobox", { name: "Class" }));
	const options = within(await screen.findByRole("listbox")).getAllByRole("option").map((o) => o.textContent ?? "");
	fireEvent.keyDown(screen.getByRole("listbox"), { key: "Escape" });
	await waitFor(() => expect(screen.queryByRole("listbox")).toBeNull());
	return options;
}

describe("ExamDetailPage campus scope", () => {
	it("offers every class in the Add schedule picker under All campuses", async () => {
		renderPage();
		await openAddSchedule();
		await waitFor(async () => expect(await dialogClassOptions()).toEqual(["LKG Main", "LKG Branch"]));
	});

	it("limits the Add schedule class picker to the picked campus", async () => {
		act(() => setSelectedCampusId(1));
		renderPage();
		await openAddSchedule();
		await waitFor(async () => expect(await dialogClassOptions()).toEqual(["LKG Main"]));
	});

	it("keeps naming another campus's class in the existing schedules table (display only)", async () => {
		act(() => setSelectedCampusId(1));
		renderPage();
		fireEvent.click(await screen.findByRole("tab", { name: "Schedule" }));
		const cell = await screen.findByRole("cell", { name: "LKG Branch" });
		expect(cell).toBeTruthy();
	});
});
