import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { PromotionPreviewClass } from "../../api/promotions";
import * as AuthContext from "../../auth/AuthContext";
import { setSelectedCampusId } from "../../layout/selectedCampus";

vi.mock("../../api/academicYears", () => ({
	listAcademicYears: vi.fn().mockResolvedValue([
		{ id: 1, name: "2026-27", startDate: "2026-06-01", endDate: "2027-03-31", current: true, status: "OPEN" },
		{ id: 2, name: "2027-28", startDate: "2027-06-01", endDate: "2028-03-31", current: false, status: "OPEN" },
	]),
}));
vi.mock("../../api/schoolClasses", () => ({
	listSchoolClasses: vi.fn(async (yearId: number) =>
		yearId === 1
			? [
					{ id: 10, academicYearId: 1, campusId: 1, campusName: "Main Campus", gradeLevelId: 1, displayName: "Grade 1 Main", status: "ACTIVE" },
					{ id: 11, academicYearId: 1, campusId: 2, campusName: "Peresandra Branch", gradeLevelId: 1, displayName: "Grade 1 Branch", status: "ACTIVE" },
				]
			: [
					{ id: 20, academicYearId: 2, campusId: 1, campusName: "Main Campus", gradeLevelId: 2, displayName: "Grade 2 Main", status: "ACTIVE" },
					{ id: 21, academicYearId: 2, campusId: 2, campusName: "Peresandra Branch", gradeLevelId: 2, displayName: "Grade 2 Branch", status: "ACTIVE" },
				],
	),
}));
vi.mock("../../api/sections", () => ({
	listSections: vi.fn(async (classId: number) => [{ id: classId * 10, schoolClassId: classId, name: "A", capacity: 30, room: null, status: "ACTIVE" }]),
}));
vi.mock("../../api/promotions", () => ({
	previewPromotion: vi.fn(),
	applyPromotions: vi.fn(),
}));
vi.mock("../../utils/useSchoolClassLabel", () => ({
	useSchoolClassLabel: () => (c: { displayName: string | null }) => c.displayName ?? "Class",
}));

import { applyPromotions, previewPromotion } from "../../api/promotions";
import { StudentPromotionPage } from "./StudentPromotionPage";

function previewClass(id: number, target: number, label: string, studentId: number, name: string): PromotionPreviewClass {
	return {
		sourceSchoolClassId: id,
		label,
		proposedTargetSchoolClassId: target,
		proposedAction: "PROMOTE",
		reason: null,
		targetSections: [{ id: target * 10, name: "A", remainingCapacity: null }],
		students: [{ studentId, name, admissionNumber: `A${studentId}`, currentSectionId: id * 10, proposedAction: "PROMOTE", reason: "passed", suggestedTargetSectionId: target * 10 }],
	};
}

const PREVIEW = [previewClass(10, 20, "Grade 1 Main", 1, "Asha Main"), previewClass(11, 21, "Grade 1 Branch", 2, "Ben Branch")];

beforeEach(() => {
	vi.clearAllMocks();
	act(() => setSelectedCampusId(null));
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({ hasPermission: () => true } as unknown as ReturnType<typeof AuthContext.useAuth>);
	vi.mocked(previewPromotion).mockResolvedValue(PREVIEW);
	vi.mocked(applyPromotions).mockResolvedValue([]);
});

async function openClassPlan() {
	render(<StudentPromotionPage />);
	await waitFor(() => expect(screen.getByRole("combobox", { name: "To year" })).toHaveTextContent("2027-28"));
	fireEvent.click(screen.getByRole("button", { name: "Preview" }));
	await screen.findAllByRole("combobox", { name: /Target class for/ });
}

describe("StudentPromotionPage campus scope", () => {
	it("shows every campus's classes and no note under All campuses", async () => {
		await openClassPlan();
		expect(screen.getByRole("combobox", { name: "Target class for Grade 1 Main" })).toBeInTheDocument();
		expect(screen.getByRole("combobox", { name: "Target class for Grade 1 Branch" })).toBeInTheDocument();
		expect(screen.queryByText(/Showing .* only/)).not.toBeInTheDocument();
	});

	it("limits the plan rows and the target-class choices to the picked campus, with a note", async () => {
		act(() => setSelectedCampusId(1));
		await openClassPlan();

		expect(screen.getByRole("combobox", { name: "Target class for Grade 1 Main" })).toBeInTheDocument();
		expect(screen.queryByRole("combobox", { name: "Target class for Grade 1 Branch" })).not.toBeInTheDocument();
		expect(screen.getByText("Showing Main Campus only - switch to All campuses to include others.")).toBeInTheDocument();

		fireEvent.mouseDown(screen.getByRole("combobox", { name: "Target class for Grade 1 Main" }));
		const options = screen.getAllByRole("option").map((o) => o.textContent);
		expect(options).toEqual(["Grade 2 Main", "Graduate"]);
	});

	it("applies only the picked campus's students and follows a campus switch", async () => {
		act(() => setSelectedCampusId(2));
		await openClassPlan();
		fireEvent.click(screen.getByRole("button", { name: "Continue to students" }));
		await screen.findByRole("combobox", { name: "Decision for Ben Branch" });
		expect(screen.queryByRole("combobox", { name: "Decision for Asha Main" })).not.toBeInTheDocument();

		fireEvent.click(screen.getByRole("button", { name: "Apply" }));
		await waitFor(() => expect(applyPromotions).toHaveBeenCalledTimes(1));
		expect(vi.mocked(applyPromotions).mock.calls[0][0].decisions).toEqual([
			{ studentId: 2, action: "PROMOTE", targetSectionId: 210, rollNumber: null },
		]);
	});

	it("includes every campus's students under All campuses", async () => {
		await openClassPlan();
		fireEvent.click(screen.getByRole("button", { name: "Continue to students" }));
		await screen.findByRole("combobox", { name: "Decision for Ben Branch" });
		fireEvent.click(screen.getByRole("button", { name: "Apply" }));
		await waitFor(() => expect(applyPromotions).toHaveBeenCalledTimes(1));
		expect(vi.mocked(applyPromotions).mock.calls[0][0].decisions.map((d) => d.studentId)).toEqual([1, 2]);
	});
});
