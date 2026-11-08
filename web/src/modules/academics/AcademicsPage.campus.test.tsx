import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";
import { setSelectedCampusId } from "../../layout/selectedCampus";

const CLASSES = [
	{ id: 1, academicYearId: 1, campusId: 1, campusName: "Main Campus", gradeLevelId: 1, displayName: null, status: "ACTIVE" },
	{ id: 2, academicYearId: 1, campusId: 2, campusName: "Peresandra Branch", gradeLevelId: 1, displayName: null, status: "ACTIVE" },
];

vi.mock("../../api/schoolClasses", () => ({
	listSchoolClasses: vi.fn(async () => CLASSES),
	changeSchoolClassStatus: vi.fn(),
	createSchoolClass: vi.fn(),
	updateSchoolClassDisplayName: vi.fn(),
}));
vi.mock("../../api/gradeLevels", () => ({
	listGradeLevels: vi.fn(async () => [{ id: 1, name: "LKG", sequence: 1 }]),
}));
vi.mock("../../api/campuses", () => ({
	listCampuses: vi.fn(async () => [
		{ id: 1, name: "Main Campus" },
		{ id: 2, name: "Peresandra Branch" },
	]),
}));
vi.mock("../../api/sections", () => ({
	listSections: vi.fn(async () => []),
}));
vi.mock("../../api/persons", () => ({ listPersons: vi.fn(async () => []) }));
vi.mock("../../api/enrollments", () => ({ listSectionEnrollments: vi.fn(async () => []) }));
vi.mock("../../api/teacherAssignments", () => ({ listTeacherAssignmentsForSection: vi.fn(async () => []) }));
vi.mock("./SectionsPanel", () => ({
	SectionsPanel: ({ title }: { title: string }) => <div data-testid="sections-card">{title}</div>,
}));

import { AcademicsPage } from "./AcademicsPage";

beforeEach(() => {
	// The panels gate their action buttons with <Can>, which needs an auth context; this suite is about campus scoping, so grant everything.
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasPermission: () => true,
		hasAnyPermission: () => true,
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
	act(() => setSelectedCampusId(null));
});

function openTab(name: string) {
	fireEvent.click(screen.getByRole("tab", { name }));
}

describe("AcademicsPage campus scope", () => {
	it("shows a Sections card per class, campus-labelled, under All campuses", async () => {
		render(<AcademicsPage />);
		openTab("Sections");
		await waitFor(() => expect(screen.getAllByTestId("sections-card")).toHaveLength(2));
		expect(screen.getAllByTestId("sections-card").map((c) => c.textContent)).toEqual(["LKG - Main Campus", "LKG - Peresandra Branch"]);
	});

	it("shows only the picked campus's Sections cards, and follows a campus switch", async () => {
		act(() => setSelectedCampusId(2));
		render(<AcademicsPage />);
		openTab("Sections");
		await waitFor(() => expect(screen.getAllByTestId("sections-card")).toHaveLength(1));
		expect(screen.getByTestId("sections-card")).toHaveTextContent("Peresandra Branch");

		act(() => setSelectedCampusId(1));
		await waitFor(() => expect(screen.getByTestId("sections-card")).toHaveTextContent("Main Campus"));
		expect(screen.getAllByTestId("sections-card")).toHaveLength(1);
	});

	it("limits the Teacher assignments class picker to the picked campus and resets its pick on a switch", async () => {
		act(() => setSelectedCampusId(null));
		render(<AcademicsPage />);
		openTab("Teacher assignments");
		const picker = await screen.findByRole("combobox", { name: "Class offering" });
		await waitFor(() => expect(picker).toHaveTextContent("LKG - Main Campus"));

		act(() => setSelectedCampusId(2));
		await waitFor(() => expect(picker).toHaveTextContent("LKG - Peresandra Branch"));

		fireEvent.mouseDown(picker);
		expect(within(screen.getByRole("listbox")).getAllByRole("option").map((o) => o.textContent)).toEqual(["LKG - Peresandra Branch"]);
	});

	it("explains an empty Sections tab for a campus with no classes", async () => {
		act(() => setSelectedCampusId(99));
		render(<AcademicsPage />);
		openTab("Sections");
		await screen.findByText(/No class offerings in the selected campus yet/);
	});
});
