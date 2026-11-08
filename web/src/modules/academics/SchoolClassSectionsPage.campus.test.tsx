import { act, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { setSelectedCampusId } from "../../layout/selectedCampus";

const CLASSES = [
	{ id: 1, academicYearId: 1, campusId: 1, campusName: "Main Campus", gradeLevelId: 1, displayName: "LKG Main", status: "ACTIVE" },
	{ id: 2, academicYearId: 1, campusId: 2, campusName: "Branch", gradeLevelId: 1, displayName: "LKG Branch", status: "ACTIVE" },
];

vi.mock("../../api/schoolClasses", () => ({ listSchoolClasses: vi.fn(async () => CLASSES) }));
vi.mock("./ClassSubjectsPanel", () => ({ ClassSubjectsPanel: () => null }));
vi.mock("./SectionsPanel", () => ({ SectionsPanel: () => null }));
vi.mock("./classLabel", () => ({
	useClassLabel: () => (c: { displayName: string | null }) => c.displayName ?? "Class",
}));

import { SchoolClassSectionsPage } from "./SchoolClassSectionsPage";

beforeEach(() => {
	act(() => setSelectedCampusId(null));
});

describe("SchoolClassSectionsPage campus scope", () => {
	it("still opens a deep-linked class that belongs to another campus (display only, never filtered)", async () => {
		act(() => setSelectedCampusId(1));
		render(
			<MemoryRouter initialEntries={["/academics/classes/2"]}>
				<Routes>
					<Route path="/academics/classes/:classId" element={<SchoolClassSectionsPage />} />
				</Routes>
			</MemoryRouter>,
		);
		await waitFor(() => expect(screen.getByRole("heading", { level: 1 }).textContent).toBe("LKG Branch"));
	});
});
