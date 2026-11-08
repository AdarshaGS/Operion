import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";
import { setSelectedCampusId } from "../../layout/selectedCampus";

vi.mock("../../api/schoolClasses", () => ({
	listSchoolClasses: vi.fn().mockResolvedValue([]),
}));
vi.mock("../../api/sections", () => ({
	listSections: vi.fn().mockResolvedValue([]),
}));
vi.mock("../../api/students", async (importOriginal) => ({
	...(await importOriginal<typeof import("../../api/students")>()),
	searchStudents: vi.fn(),
}));
vi.mock("../../utils/useSchoolClassLabel", () => ({
	useSchoolClassLabel: () => (c: { displayName: string | null }) => c.displayName ?? "Class",
}));

import { searchStudents } from "../../api/students";
import { StudentListPage } from "./StudentListPage";

function lastFilters() {
	const calls = vi.mocked(searchStudents).mock.calls;
	return calls[calls.length - 1][0];
}

function renderPage() {
	return render(
		<MemoryRouter>
			<StudentListPage />
		</MemoryRouter>,
	);
}

beforeEach(() => {
	vi.clearAllMocks();
	act(() => setSelectedCampusId(null));
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({ hasPermission: () => true, hasAnyPermission: () => true } as unknown as ReturnType<typeof AuthContext.useAuth>);
	vi.mocked(searchStudents).mockResolvedValue({
		content: [
			{
				id: 1,
				personId: 1,
				firstName: "Asha",
				lastName: "Rao",
				studentId: "S-1",
				admissionNumber: "ADM-1",
				admissionDate: "2025-06-01",
				status: "ACTIVE",
				sectionId: null,
				sectionName: null,
				schoolClassId: null,
				schoolClassDisplayName: null,
				primaryGuardianName: null,
				primaryGuardianPhone: null,
			},
		],
		page: 0,
		size: 25,
		totalElements: 60,
		totalPages: 3,
	} as unknown as Awaited<ReturnType<typeof searchStudents>>);
});

describe("StudentListPage campus filter", () => {
	it("sends no campusId under All campuses", async () => {
		renderPage();
		await waitFor(() => expect(searchStudents).toHaveBeenCalled());
		expect(lastFilters().campusId).toBeNull();
	});

	it("sends the top-bar campus and refetches when it changes", async () => {
		act(() => setSelectedCampusId(2));
		renderPage();
		await waitFor(() => expect(searchStudents).toHaveBeenCalled());
		expect(lastFilters().campusId).toBe(2);

		act(() => setSelectedCampusId(5));
		await waitFor(() => expect(lastFilters().campusId).toBe(5));

		act(() => setSelectedCampusId(null));
		await waitFor(() => expect(lastFilters().campusId).toBeNull());
	});

	it("returns to the first page when the campus changes", async () => {
		act(() => setSelectedCampusId(1));
		renderPage();
		await waitFor(() => expect(screen.getByLabelText("Go to next page")).not.toBeDisabled());

		fireEvent.click(screen.getByLabelText("Go to next page"));
		await waitFor(() => expect(lastFilters().page).toBe(1));
		expect(lastFilters().campusId).toBe(1);

		act(() => setSelectedCampusId(2));
		await waitFor(() => expect(lastFilters()).toMatchObject({ campusId: 2, page: 0 }));
	});
});
