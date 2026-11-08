import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const SUBMITTED_PLAN = {
	id: 1,
	sectionId: 1,
	className: "Grade 5",
	sectionName: "A",
	subjectId: 1,
	subjectName: "Maths",
	planDate: "2026-11-10",
	title: "Fractions",
	objectives: null,
	activities: null,
	resources: null,
	status: "SUBMITTED",
	authorName: "Asha Rao",
	submittedAt: null,
	reviewNote: null,
	reviewedByName: null,
	reviewedAt: null,
};

vi.mock("../../api/learning", () => ({
	listLessonPlans: vi.fn(async () => [SUBMITTED_PLAN]),
	getLessonPlanCalendar: vi.fn(async () => []),
	approveLessonPlan: vi.fn(),
	rejectLessonPlan: vi.fn(),
	createLessonPlan: vi.fn(),
	editLessonPlan: vi.fn(),
	submitLessonPlan: vi.fn(),
}));

import { LessonPlansPanel } from "./LessonPlansPanel";

const baseScope = { manager: false, reviewer: false, sections: [], subjects: [] };

afterEach(() => vi.restoreAllMocks());

// Learning endpoints carry no @RequirePermission: the server resolves the caller into a scope (LearningAccessService) -
// scope.reviewer is LESSON_PLAN_REVIEW (or owner / ALL_FUNCTIONS), which is exactly what LessonPlanService.approve/reject require.
describe("LessonPlansPanel review permission (scope.reviewer = LESSON_PLAN_REVIEW)", () => {
	it("offers Approve and Reject on a submitted plan to a reviewer", async () => {
		render(<LessonPlansPanel scope={{ ...baseScope, reviewer: true }} />);
		fireEvent.click(await screen.findByRole("button", { name: "Review" }));
		expect(await screen.findByRole("button", { name: "Approve" })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Reject" })).toBeInTheDocument();
	});

	it("only lets a non-reviewer view the plan - no Approve / Reject", async () => {
		render(<LessonPlansPanel scope={baseScope} />);
		fireEvent.click(await screen.findByRole("button", { name: "View" }));
		expect(await screen.findByRole("button", { name: "Close" })).toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Reject" })).not.toBeInTheDocument();
	});
});
