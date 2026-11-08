import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { PromotionOutcome, PromotionPreviewClass } from "../../api/promotions";
import { ApiError } from "../../api/client";
import * as AuthContext from "../../auth/AuthContext";

vi.mock("../../api/academicYears", () => ({
	listAcademicYears: vi.fn().mockResolvedValue([
		{ id: 1, name: "2026-27", startDate: "2026-06-01", endDate: "2027-03-31", current: true, status: "OPEN" },
		{ id: 2, name: "2027-28", startDate: "2027-06-01", endDate: "2028-03-31", current: false, status: "OPEN" },
	]),
}));
vi.mock("../../api/schoolClasses", () => ({
	listSchoolClasses: vi.fn(async (yearId: number) =>
		yearId === 1
			? [{ id: 10, academicYearId: 1, campusId: 1, gradeLevelId: 1, displayName: "Grade 1", status: "ACTIVE" }]
			: [{ id: 20, academicYearId: 2, campusId: 1, gradeLevelId: 2, displayName: "Grade 2", status: "ACTIVE" }],
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

const PREVIEW: PromotionPreviewClass[] = [
	{
		sourceSchoolClassId: 10,
		label: "Grade 1",
		proposedTargetSchoolClassId: 20,
		proposedAction: "PROMOTE",
		reason: null,
		targetSections: [{ id: 200, name: "A", remainingCapacity: 0 }],
		students: [
			{ studentId: 1, name: "Asha Rao", admissionNumber: "A1", currentSectionId: 100, proposedAction: "NEEDS_DECISION", reason: "no published final result", suggestedTargetSectionId: 200 },
			{ studentId: 2, name: "Ben Roy", admissionNumber: "A2", currentSectionId: 100, proposedAction: "PROMOTE", reason: "passed final exam", suggestedTargetSectionId: 200 },
			{ studentId: 3, name: "Cy Das", admissionNumber: "A3", currentSectionId: 100, proposedAction: "ALREADY_DONE", reason: "already has an enrollment in the target year", suggestedTargetSectionId: null },
		],
	},
];

const outcome = (studentId: number, succeeded: boolean, error: string | null = null): PromotionOutcome => ({
	studentId,
	action: "PROMOTE",
	succeeded,
	skipped: false,
	error,
});

function choose(name: string, option: string) {
	fireEvent.mouseDown(screen.getByRole("combobox", { name }));
	fireEvent.click(within(screen.getByRole("listbox")).getByRole("option", { name: option }));
}

beforeEach(() => {
	vi.clearAllMocks();
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasPermission: () => true,
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
	vi.mocked(previewPromotion).mockResolvedValue(PREVIEW);
});

describe("StudentPromotionPage wizard", () => {
	it("previews, defaults exceptions to Promote, applies, and retries only the failed student", async () => {
		vi.mocked(applyPromotions)
			.mockResolvedValueOnce([outcome(1, true), outcome(2, false, "Section A is full")])
			.mockResolvedValueOnce([outcome(2, true)]);

		render(<StudentPromotionPage />);
		await waitFor(() => expect(screen.getByRole("combobox", { name: "To year" })).toHaveTextContent("2027-28"));
		fireEvent.click(screen.getByRole("button", { name: "Preview" }));

		// Step 2: class plan with the server's default target.
		await screen.findByRole("combobox", { name: "Target class for Grade 1" });
		expect(previewPromotion).toHaveBeenCalledWith({ sourceAcademicYearId: 1, targetAcademicYearId: 2, effectiveDate: "2027-03-31" });
		fireEvent.click(screen.getByRole("button", { name: "Continue to students" }));

		// Step 3: the exception already defaults to Promote, so Apply is enabled; the full section is flagged.
		await screen.findByRole("combobox", { name: "Decision for Asha Rao" });
		expect(screen.getAllByText("Section full")).toHaveLength(2);
		expect(screen.getByRole("combobox", { name: "Decision for Asha Rao" })).toHaveTextContent("Promote");
		expect(screen.getByRole("combobox", { name: "Decision for Cy Das" })).toHaveAttribute("aria-disabled", "true");
		expect(screen.getByRole("button", { name: "Apply" })).toBeEnabled();

		fireEvent.click(screen.getByRole("button", { name: "Apply" }));
		await screen.findByText("1 succeeded, 0 skipped (already applied), 1 failed.");
		expect(applyPromotions).toHaveBeenLastCalledWith({
			targetAcademicYearId: 2,
			promotionDate: "2027-03-31",
			decisions: [
				{ studentId: 1, action: "PROMOTE", targetSectionId: 200, rollNumber: null },
				{ studentId: 2, action: "PROMOTE", targetSectionId: 200, rollNumber: null },
			],
		});
		expect(screen.getByText(/Section A is full/)).toBeInTheDocument();

		fireEvent.click(screen.getByRole("button", { name: "Retry failed" }));
		await screen.findByText("2 succeeded, 0 skipped (already applied), 0 failed.");
		expect(applyPromotions).toHaveBeenLastCalledWith({
			targetAcademicYearId: 2,
			promotionDate: "2027-03-31",
			decisions: [{ studentId: 2, action: "PROMOTE", targetSectionId: 200, rollNumber: null }],
		});
		expect(screen.queryByRole("button", { name: "Retry failed" })).not.toBeInTheDocument();
	});

	it("re-previews with the edited class mapping when a class target changes", async () => {
		render(<StudentPromotionPage />);
		await waitFor(() => expect(screen.getByRole("combobox", { name: "To year" })).toHaveTextContent("2027-28"));
		fireEvent.click(screen.getByRole("button", { name: "Preview" }));
		await screen.findByRole("combobox", { name: "Target class for Grade 1" });

		vi.mocked(previewPromotion).mockResolvedValueOnce([
			{ ...PREVIEW[0], proposedAction: "GRADUATE", proposedTargetSchoolClassId: null, reason: "last grade", targetSections: [] },
		]);
		choose("Target class for Grade 1", "Graduate");

		await waitFor(() =>
			expect(previewPromotion).toHaveBeenLastCalledWith({
				sourceAcademicYearId: 1,
				targetAcademicYearId: 2,
				effectiveDate: "2027-03-31",
				classMap: [{ sourceSchoolClassId: 10, targetSchoolClassId: null }],
			}),
		);
		await screen.findByText("last grade");
	});

	it("flags a class with no target class and shows the API error for a rejected preview", async () => {
		vi.mocked(previewPromotion).mockRejectedValueOnce(new ApiError(409, "The target academic year is closed"));
		render(<StudentPromotionPage />);
		await waitFor(() => expect(screen.getByRole("combobox", { name: "To year" })).toHaveTextContent("2027-28"));
		fireEvent.click(screen.getByRole("button", { name: "Preview" }));
		expect(await screen.findByText("The target academic year is closed")).toBeInTheDocument();
		expect(screen.queryByRole("combobox", { name: "Target class for Grade 1" })).not.toBeInTheDocument();

		vi.mocked(previewPromotion).mockResolvedValueOnce([
			{ ...PREVIEW[0], proposedTargetSchoolClassId: null, reason: "no Grade 2 class in target year", targetSections: [] },
		]);
		fireEvent.click(screen.getByRole("button", { name: "Preview" }));
		expect(await screen.findByText("No target class")).toBeInTheDocument();
		expect(screen.getByText("no Grade 2 class in target year")).toBeInTheDocument();
	});

	async function openStudentsStep() {
		render(<StudentPromotionPage />);
		await waitFor(() => expect(screen.getByRole("combobox", { name: "To year" })).toHaveTextContent("2027-28"));
		fireEvent.click(screen.getByRole("button", { name: "Preview" }));
		await screen.findByRole("combobox", { name: "Target class for Grade 1" });
		fireEvent.click(screen.getByRole("button", { name: "Continue to students" }));
		await screen.findByRole("combobox", { name: "Decision for Asha Rao" });
	}

	const summaryText = () => screen.getByTestId("choice-summary").textContent;

	it("sets Promote as the default on a needs-decision row and shows the live tally", async () => {
		await openStudentsStep();
		expect(screen.getByRole("combobox", { name: "Decision for Asha Rao" })).toHaveTextContent("Promote");
		expect(screen.getByRole("combobox", { name: "Decision for Ben Roy" })).toHaveTextContent("Promote");
		expect(summaryText()).toBe("Promote 2 - Repeat 0 - Graduate 0 - Skip 0 - Undecided 0 - Already done 1");
	});

	it("Set all changes eligible rows only: not already-done rows, and Promote not rows without a target", async () => {
		const noTarget: PromotionPreviewClass = {
			...PREVIEW[0],
			sourceSchoolClassId: 11,
			label: "Grade 5",
			proposedTargetSchoolClassId: null,
			reason: "no Grade 6 class in target year",
			targetSections: [],
			students: [
				{ studentId: 4, name: "Di Sen", admissionNumber: "A4", currentSectionId: 110, proposedAction: "NEEDS_DECISION", reason: "no published final result", suggestedTargetSectionId: null },
			],
		};
		vi.mocked(previewPromotion).mockResolvedValue([PREVIEW[0], noTarget]);
		await openStudentsStep();

		// The row with nowhere to go stays undecided, says why, and keeps Apply disabled.
		expect(screen.getByRole("combobox", { name: "Decision for Di Sen" })).not.toHaveTextContent(/Promote|Repeat|Graduate|Skip/);
		expect(screen.getByText(/No target class to promote into/)).toBeInTheDocument();
		expect(summaryText()).toContain("Undecided 1");
		expect(screen.getByRole("button", { name: "Apply" })).toBeDisabled();

		choose("Set all to", "Skip");
		fireEvent.click(screen.getByRole("button", { name: "Set all" }));
		expect(summaryText()).toBe("Promote 0 - Repeat 0 - Graduate 0 - Skip 3 - Undecided 0 - Already done 1");
		expect(screen.getByRole("combobox", { name: "Decision for Cy Das" })).toHaveTextContent("Skip");

		// Promote reaches the two rows with a target, not the one without.
		choose("Set all to", "Promote");
		fireEvent.click(screen.getByRole("button", { name: "Set all" }));
		expect(summaryText()).toBe("Promote 2 - Repeat 0 - Graduate 0 - Skip 1 - Undecided 0 - Already done 1");
		expect(screen.getByRole("combobox", { name: "Decision for Di Sen" })).toHaveTextContent("Skip");
		expect(screen.getByRole("combobox", { name: "Decision for Cy Das" })).toHaveTextContent("Skip");
	});

	it("keeps Apply disabled while a row without a target stays undecided, then enables it once decided", async () => {
		const noTarget: PromotionPreviewClass = {
			...PREVIEW[0],
			sourceSchoolClassId: 11,
			label: "Grade 5",
			proposedTargetSchoolClassId: null,
			targetSections: [],
			students: [{ studentId: 4, name: "Di Sen", admissionNumber: "A4", currentSectionId: 110, proposedAction: "NEEDS_DECISION", reason: "", suggestedTargetSectionId: null }],
		};
		vi.mocked(previewPromotion).mockResolvedValue([PREVIEW[0], noTarget]);
		await openStudentsStep();

		choose("Set all to", "Promote");
		fireEvent.click(screen.getByRole("button", { name: "Set all" }));
		expect(screen.getByRole("button", { name: "Apply" })).toBeDisabled();
		expect(screen.getByText("1 student needs a decision.")).toBeInTheDocument();

		choose("Decision for Di Sen", "Skip");
		expect(screen.getByRole("button", { name: "Apply" })).toBeEnabled();
	});

	it("keeps a per-row override across re-renders, Back/Continue and a class re-preview", async () => {
		await openStudentsStep();
		choose("Decision for Ben Roy", "Skip");
		expect(summaryText()).toContain("Skip 1");

		// A re-render caused by other state (the bulk dropdown) does not touch the row.
		choose("Set all to", "Repeat");
		expect(screen.getByRole("combobox", { name: "Decision for Ben Roy" })).toHaveTextContent("Skip");

		// Leaving the step and coming back.
		fireEvent.click(screen.getByRole("button", { name: "Back" }));
		await screen.findByRole("combobox", { name: "Target class for Grade 1" });
		fireEvent.click(screen.getByRole("button", { name: "Continue to students" }));
		await screen.findByRole("combobox", { name: "Decision for Ben Roy" });
		expect(screen.getByRole("combobox", { name: "Decision for Ben Roy" })).toHaveTextContent("Skip");

		// A fresh preview where this class's plan is unchanged keeps the admin's choice.
		fireEvent.click(screen.getByRole("button", { name: "Back" }));
		await screen.findByRole("combobox", { name: "Target class for Grade 1" });
		choose("Target class for Grade 1", "Graduate");
		await waitFor(() => expect(previewPromotion).toHaveBeenCalledTimes(2));
		fireEvent.click(screen.getByRole("button", { name: "Continue to students" }));
		await screen.findByRole("combobox", { name: "Decision for Ben Roy" });
		expect(screen.getByRole("combobox", { name: "Decision for Ben Roy" })).toHaveTextContent("Skip");
	});
});
