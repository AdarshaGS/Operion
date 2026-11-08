import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";

vi.mock("../../api/subjects", () => ({
	listSubjects: vi.fn(async () => [{ id: 1, name: "Mathematics", code: "MATH", status: "ACTIVE" }]),
	createSubject: vi.fn(),
	changeSubjectStatus: vi.fn(),
}));
vi.mock("../../api/gradeLevels", () => ({
	listGradeLevels: vi.fn(async () => [{ id: 1, name: "Grade 5", sequenceOrder: 5, stage: null, status: "ACTIVE" }]),
	createGradeLevel: vi.fn(),
	updateGradeLevel: vi.fn(),
	changeGradeLevelStatus: vi.fn(),
}));
vi.mock("../../api/sections", () => ({
	listSections: vi.fn(async () => [{ id: 1, schoolClassId: 1, name: "A", capacity: 40, room: null, status: "ACTIVE" }]),
	createSection: vi.fn(),
	updateSection: vi.fn(),
	changeSectionStatus: vi.fn(),
}));
vi.mock("../../api/schoolClasses", () => ({
	listSchoolClasses: vi.fn(async () => [
		{ id: 1, academicYearId: 1, campusId: 1, campusName: "Main", gradeLevelId: 1, displayName: "Class Five", status: "ACTIVE" },
	]),
	createSchoolClass: vi.fn(),
	changeSchoolClassStatus: vi.fn(),
	updateSchoolClassDisplayName: vi.fn(),
}));
vi.mock("../../api/academicYears", () => ({ listAcademicYears: vi.fn(async () => []) }));
vi.mock("../../api/campuses", () => ({ listCampuses: vi.fn(async () => []) }));
vi.mock("../../api/classSubjects", () => ({
	listClassSubjects: vi.fn(async () => [{ id: 1, schoolClassId: 1, subjectId: 1, mandatory: true, weeklyPeriods: 5, status: "ACTIVE" }]),
	assignSubjectToClass: vi.fn(),
	changeClassSubjectStatus: vi.fn(),
	updateClassSubject: vi.fn(),
}));
vi.mock("../../api/persons", () => ({ listPersons: vi.fn(async () => [{ id: 7, firstName: "Asha", lastName: "Rao" }]) }));
vi.mock("../../api/teacherAssignments", () => ({
	listTeacherAssignmentsForSection: vi.fn(async () => [
		{ id: 1, sectionId: 1, subjectId: 1, teacherPersonId: 7, assignmentType: "SUBJECT", startDate: "2026-06-01", endDate: null, status: "ACTIVE" },
	]),
	assignTeacher: vi.fn(),
	endTeacherAssignment: vi.fn(),
}));

import { ClassSubjectsPanel } from "./ClassSubjectsPanel";
import { GradeLevelsPanel } from "./GradeLevelsPanel";
import { SchoolClassesPanel } from "./SchoolClassesPanel";
import { SectionsPanel } from "./SectionsPanel";
import { SubjectsPanel } from "./SubjectsPanel";
import { TeacherAssignmentsPanel } from "./TeacherAssignmentsPanel";

function grant(...codes: string[]) {
	const held = new Set(codes);
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasPermission: (code: string) => held.has(code),
		hasAnyPermission: (wanted: string[]) => wanted.some((code) => held.has(code)),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

afterEach(() => vi.restoreAllMocks());

describe("SubjectsPanel permissions (SUBJECT_MANAGE)", () => {
	it("shows Add subject and the row status toggle to a manager", async () => {
		grant("SUBJECT_MANAGE");
		render(<SubjectsPanel />);
		await screen.findByText("Mathematics");
		expect(screen.getByRole("button", { name: /add subject/i })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Deactivate" })).toBeInTheDocument();
	});

	it("hides both from a viewer, and a different *_MANAGE code does not unlock them", async () => {
		grant("SUBJECT_VIEW", "CLASS_MANAGE");
		render(<SubjectsPanel />);
		await screen.findByText("Mathematics");
		expect(screen.queryByRole("button", { name: /add subject/i })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Deactivate" })).not.toBeInTheDocument();
	});
});

describe("GradeLevelsPanel permissions (GRADE_LEVEL_MANAGE)", () => {
	it("shows Add / Edit / Deactivate to a manager", async () => {
		grant("GRADE_LEVEL_MANAGE");
		render(<GradeLevelsPanel />);
		await screen.findByText("Grade 5");
		expect(screen.getByRole("button", { name: /add grade level/i })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Edit" })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Deactivate" })).toBeInTheDocument();
	});

	it("hides them from a viewer", async () => {
		grant("GRADE_LEVEL_VIEW");
		render(<GradeLevelsPanel />);
		await screen.findByText("Grade 5");
		expect(screen.queryByRole("button", { name: /add grade level/i })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Deactivate" })).not.toBeInTheDocument();
	});
});

describe("Class structure panels permissions (CLASS_MANAGE)", () => {
	it("SchoolClassesPanel: manager sees Add class / Edit / Deactivate, viewer sees none", async () => {
		grant("CLASS_MANAGE");
		const { unmount } = render(
			<MemoryRouter>
				<SchoolClassesPanel />
			</MemoryRouter>,
		);
		await screen.findByText("Class Five");
		expect(screen.getByRole("button", { name: /add class/i })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Edit" })).toBeInTheDocument();
		unmount();

		grant("CLASS_VIEW");
		render(
			<MemoryRouter>
				<SchoolClassesPanel />
			</MemoryRouter>,
		);
		await screen.findByText("Class Five");
		expect(screen.queryByRole("button", { name: /add class/i })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Deactivate" })).not.toBeInTheDocument();
	});

	it("SectionsPanel: Add section / Edit gated", async () => {
		grant("CLASS_MANAGE");
		const { unmount } = render(<SectionsPanel classId={1} />);
		await screen.findByText("A");
		expect(screen.getByRole("button", { name: /add section/i })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Edit" })).toBeInTheDocument();
		unmount();

		grant("CLASS_VIEW");
		render(<SectionsPanel classId={1} />);
		await screen.findByText("A");
		expect(screen.queryByRole("button", { name: /add section/i })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();
	});

	it("ClassSubjectsPanel: Assign subject / Edit gated", async () => {
		grant("CLASS_MANAGE");
		const { unmount } = render(<ClassSubjectsPanel classId={1} />);
		await screen.findByText("ACTIVE");
		expect(screen.getByRole("button", { name: /assign subject/i })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Edit" })).toBeInTheDocument();
		unmount();

		grant("CLASS_VIEW");
		render(<ClassSubjectsPanel classId={1} />);
		await screen.findByText("ACTIVE");
		expect(screen.queryByRole("button", { name: /assign subject/i })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();
	});
});

describe("TeacherAssignmentsPanel permissions (TEACHER_ASSIGNMENT_MANAGE)", () => {
	it("shows Assign teacher and End to a manager", async () => {
		grant("TEACHER_ASSIGNMENT_MANAGE");
		render(<TeacherAssignmentsPanel sectionId={1} />);
		await screen.findByText("Asha Rao");
		expect(screen.getByRole("button", { name: /assign teacher/i })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "End" })).toBeInTheDocument();
	});

	it("hides them from a viewer", async () => {
		grant("TEACHER_ASSIGNMENT_VIEW");
		render(<TeacherAssignmentsPanel sectionId={1} />);
		await screen.findByText("Asha Rao");
		expect(screen.queryByRole("button", { name: /assign teacher/i })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "End" })).not.toBeInTheDocument();
	});
});
