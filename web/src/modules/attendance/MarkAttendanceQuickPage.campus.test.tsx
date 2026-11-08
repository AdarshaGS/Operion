import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { setSelectedCampusId } from "../../layout/selectedCampus";

const CLASSES = [
	{ id: 1, academicYearId: 1, campusId: 1, campusName: "Main Campus", gradeLevelId: 1, displayName: "LKG Main", status: "ACTIVE" },
	{ id: 2, academicYearId: 1, campusId: 2, campusName: "Branch", gradeLevelId: 1, displayName: "LKG Branch", status: "ACTIVE" },
];

vi.mock("../../api/schoolClasses", () => ({ listSchoolClasses: vi.fn(async () => CLASSES) }));
vi.mock("../../api/sections", () => ({
	listSections: vi.fn(async (classId: number) => [{ id: classId * 10, schoolClassId: classId, name: `Sec ${classId}`, capacity: 30, room: null, status: "ACTIVE" }]),
}));
vi.mock("../../api/attendance", () => ({ getRegister: vi.fn(async () => null), markAttendance: vi.fn() }));
vi.mock("../../api/enrollments", () => ({ listSectionEnrollments: vi.fn(async () => []) }));
vi.mock("../../api/persons", () => ({ listPersons: vi.fn(async () => []) }));
vi.mock("../../api/students", () => ({ listStudents: vi.fn(async () => []) }));
vi.mock("./useMarkableSections", () => ({
	useMarkableSections: () => ({ markable: { scope: "ALL", sectionIds: [], schoolClassIds: [] }, canMarkSection: () => true }),
}));
vi.mock("../../utils/useSchoolClassLabel", () => ({
	useSchoolClassLabel: () => (c: { displayName: string | null }) => c.displayName ?? "Class",
}));

import { MarkAttendanceQuickPage } from "./MarkAttendanceQuickPage";

beforeEach(() => {
	act(() => setSelectedCampusId(null));
});

function renderPage() {
	return render(
		<MemoryRouter>
			<MarkAttendanceQuickPage />
		</MemoryRouter>,
	);
}

async function classOptions(): Promise<string[]> {
	fireEvent.mouseDown(screen.getByRole("combobox", { name: "Class" }));
	const options = within(await screen.findByRole("listbox")).getAllByRole("option").map((o) => o.textContent ?? "");
	fireEvent.keyDown(screen.getByRole("listbox"), { key: "Escape" });
	await waitFor(() => expect(screen.queryByRole("listbox")).toBeNull());
	return options;
}

describe("MarkAttendanceQuickPage campus scope", () => {
	it("lists every class under All campuses", async () => {
		renderPage();
		await waitFor(async () => expect(await classOptions()).toEqual(["LKG Main", "LKG Branch"]));
	});

	it("lists only the picked campus's classes", async () => {
		act(() => setSelectedCampusId(2));
		renderPage();
		await waitFor(async () => expect(await classOptions()).toEqual(["LKG Branch"]));
	});

	it("resets a class picked under another campus when the campus changes", async () => {
		act(() => setSelectedCampusId(null));
		renderPage();
		await waitFor(async () => expect((await classOptions()).length).toBe(2));
		fireEvent.mouseDown(screen.getByRole("combobox", { name: "Class" }));
		fireEvent.click(within(await screen.findByRole("listbox")).getByRole("option", { name: "LKG Main" }));
		await waitFor(() => expect(screen.getByRole("combobox", { name: "Class" })).toHaveTextContent("LKG Main"));

		act(() => setSelectedCampusId(2));
		await waitFor(() => expect(screen.getByRole("combobox", { name: "Class" })).not.toHaveTextContent("LKG Main"));
		expect(await classOptions()).toEqual(["LKG Branch"]);
	});

	it("explains a campus with no classes", async () => {
		act(() => setSelectedCampusId(99));
		renderPage();
		await screen.findByText("No classes in the selected campus.");
	});
});
