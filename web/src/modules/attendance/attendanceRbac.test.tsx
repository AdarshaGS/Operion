import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";
import { setSelectedCampusId } from "../../layout/selectedCampus";

vi.mock("../../api/attendance", async (importOriginal) => ({
	...(await importOriginal<typeof import("../../api/attendance")>()),
	getMarkableSections: vi.fn(),
	getRegister: vi.fn(),
	markAttendance: vi.fn(),
}));
vi.mock("../../api/attendanceOverview", () => ({ getAttendanceOverview: vi.fn() }));
vi.mock("../../api/schoolClasses", () => ({ listSchoolClasses: vi.fn() }));
vi.mock("../../api/sections", () => ({ listSections: vi.fn() }));
vi.mock("../../api/enrollments", () => ({ listSectionEnrollments: vi.fn() }));
vi.mock("../../api/students", () => ({ listStudents: vi.fn() }));
vi.mock("../../api/persons", () => ({ listPersons: vi.fn() }));
vi.mock("../../utils/useSchoolClassLabel", () => ({ useSchoolClassLabel: () => () => "Class 5" }));

import { getMarkableSections, getRegister } from "../../api/attendance";
import { getAttendanceOverview } from "../../api/attendanceOverview";
import { listSectionEnrollments } from "../../api/enrollments";
import { listPersons } from "../../api/persons";
import { listSchoolClasses } from "../../api/schoolClasses";
import { listSections } from "../../api/sections";
import { listStudents } from "../../api/students";
import { AttendanceOverviewPanel } from "./AttendanceOverviewPanel";
import { MarkAttendanceQuickPage } from "./MarkAttendanceQuickPage";

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasPermission: (code: string) => codes.includes(code),
		hasAnyPermission: (list: string[]) => list.some((code) => codes.includes(code)),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

beforeEach(() => {
	vi.clearAllMocks();
	act(() => setSelectedCampusId(null));
	vi.mocked(getAttendanceOverview).mockResolvedValue({
		date: "2026-02-02",
		studentsEnrolled: 0,
		present: 0,
		absent: 0,
		late: 0,
		classesNotRecorded: 0,
		classesTotal: 0,
		classes: [],
		activity: [],
	});
	vi.mocked(listSchoolClasses).mockResolvedValue([
		{ id: 1, academicYearId: 1, campusId: 1, campusName: "Main", gradeLevelId: 5, displayName: "Class 5", status: "ACTIVE" },
	]);
	vi.mocked(listSections).mockResolvedValue([{ id: 11, schoolClassId: 1, name: "A", capacity: null, room: null, status: "ACTIVE" }]);
	vi.mocked(listSectionEnrollments).mockResolvedValue([
		{ id: 21, studentId: 31, academicYearId: 1, sectionId: 11, schoolClassId: 1, rollNumber: 1, enrollmentStatus: "ACTIVE", current: true, enrolledDate: "2026-01-01", exitDate: null, feeClearanceStatus: "NOT_REQUIRED" },
	]);
	vi.mocked(listStudents).mockResolvedValue([{ id: 31, personId: 41 } as Awaited<ReturnType<typeof listStudents>>[number]]);
	vi.mocked(listPersons).mockResolvedValue([{ id: 41, firstName: "Asha", lastName: "Rao" } as Awaited<ReturnType<typeof listPersons>>[number]]);
	vi.mocked(getRegister).mockResolvedValue(null);
});

describe("AttendanceOverviewPanel", () => {
	it("shows Mark Attendance to a caller holding only ATTENDANCE_MARK_ANY_SECTION (backend accepts either mark code)", async () => {
		grant("ATTENDANCE_VIEW", "ATTENDANCE_MARK_ANY_SECTION");
		render(
			<MemoryRouter>
				<AttendanceOverviewPanel onOpenRegister={() => {}} onMarkAttendance={() => {}} />
			</MemoryRouter>,
		);

		expect(await screen.findByRole("button", { name: /mark attendance/i })).toBeInTheDocument();
	});

	it("hides Mark Attendance from a view-only caller", async () => {
		grant("ATTENDANCE_VIEW");
		render(
			<MemoryRouter>
				<AttendanceOverviewPanel onOpenRegister={() => {}} onMarkAttendance={() => {}} />
			</MemoryRouter>,
		);

		await waitFor(() => expect(getAttendanceOverview).toHaveBeenCalled());
		expect(screen.queryByRole("button", { name: /mark attendance/i })).not.toBeInTheDocument();
	});
});

describe("MarkAttendanceQuickPage submit", () => {
	async function openSection() {
		render(
			<MemoryRouter>
				<MarkAttendanceQuickPage />
			</MemoryRouter>,
		);
		fireEvent.mouseDown(await screen.findByRole("combobox", { name: "Class" }));
		fireEvent.click(await screen.findByRole("option", { name: "Class 5" }));
		await waitFor(() => expect(listSections).toHaveBeenCalled());
		fireEvent.mouseDown(screen.getByRole("combobox", { name: "Section" }));
		fireEvent.click(await screen.findByRole("option", { name: "A" }));
		await screen.findByText(/1 student/);
	}

	it("enables Submit when the markable scope covers the section", async () => {
		grant("ATTENDANCE_VIEW", "ATTENDANCE_MARK_ANY_SECTION");
		vi.mocked(getMarkableSections).mockResolvedValue({ scope: "ALL", sectionIds: [], schoolClassIds: [] });
		await openSection();

		expect(screen.getByRole("button", { name: "Submit" })).toBeEnabled();
		expect(screen.getByRole("button", { name: /mark all present/i })).toBeEnabled();
	});

	it("disables Submit and Mark all present when the caller can mark nothing (scope NONE)", async () => {
		grant("ATTENDANCE_VIEW");
		vi.mocked(getMarkableSections).mockResolvedValue({ scope: "NONE", sectionIds: [], schoolClassIds: [] });
		await openSection();

		expect(screen.getByRole("button", { name: "Submit" })).toBeDisabled();
		expect(screen.getByRole("button", { name: /mark all present/i })).toBeDisabled();
		expect(within(screen.getByRole("alert", { hidden: false })).getByText(/permission to mark attendance/i)).toBeInTheDocument();
	});
});
