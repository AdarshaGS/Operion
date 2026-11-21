import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";

const NEED = {
	date: "2026-11-12",
	dayOfWeek: "THURSDAY",
	entryId: 5,
	sectionId: 2,
	sectionName: "A",
	className: "Grade 5",
	subjectName: "Mathematics",
	periodId: 3,
	periodLabel: "Period 2",
	startTime: "09:00:00",
	endTime: "09:45:00",
	substitution: null,
	candidates: [{ personId: 9, name: "Ravi Iyer", rank: "Teaches the section" }],
};

vi.mock("../../api/leaveRequests", () => ({
	listLeaveRequests: vi.fn().mockResolvedValue([
		{ id: 1, staffProfileId: 11, leaveTypeId: 1, academicYearId: 1, startDate: "2026-11-12", endDate: "2026-11-12", numberOfDays: 0.5, reason: null, status: "PENDING", approvedBy: null, decidedAt: null },
	]),
	approveLeaveRequest: vi.fn(),
	rejectLeaveRequest: vi.fn(),
}));
vi.mock("../../api/leaveTypes", () => ({ listLeaveTypes: vi.fn().mockResolvedValue([{ id: 1, code: "CL", name: "Casual leave" }]) }));
vi.mock("../../api/persons", () => ({ listPersons: vi.fn().mockResolvedValue([{ id: 7, firstName: "Asha", lastName: "Rao" }]) }));
vi.mock("../../api/staffProfiles", () => ({ listStaffProfiles: vi.fn().mockResolvedValue([{ id: 11, personId: 7, employeeCode: "E11" }]) }));
vi.mock("../../api/calendar", () => ({ getCalendarDays: vi.fn().mockResolvedValue({ nonWorkingDays: [] }) }));
vi.mock("../../api/timetable", () => ({ listSubstitutionNeeds: vi.fn() }));

import { listSubstitutionNeeds } from "../../api/timetable";
import { LeaveRequestsInboxPanel } from "./LeaveRequestsInboxPanel";

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasPermission: (code: string) => codes.includes(code),
		hasAnyPermission: (required: string[]) => required.some((code) => codes.includes(code)),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

function renderInbox() {
	return render(
		<MemoryRouter>
			<LeaveRequestsInboxPanel />
		</MemoryRouter>,
	);
}

beforeEach(() => vi.mocked(listSubstitutionNeeds).mockReset().mockResolvedValue([NEED]));
afterEach(() => vi.restoreAllMocks());

describe("LeaveCoverPanel in the leave inbox", () => {
	it("hides the cover panel without TIMETABLE_VIEW but still renders Approve and Reject", async () => {
		grant("HR_LEAVE_MANAGE");
		renderInbox();
		fireEvent.click(await screen.findByRole("button", { name: "Timetable cover" }));
		expect(await screen.findByText("Timetable cover isn't visible to your role")).toBeInTheDocument();
		expect(listSubstitutionNeeds).not.toHaveBeenCalled();
		expect(screen.queryByText("Mathematics")).not.toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Approve" })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Reject" })).toBeInTheDocument();
	});

	it("lists the missed periods with TIMETABLE_VIEW, notes half-day leave, and offers Assign cover only to a timetable manager", async () => {
		grant("HR_LEAVE_MANAGE", "TIMETABLE_VIEW");
		renderInbox();
		fireEvent.click(await screen.findByRole("button", { name: "Timetable cover" }));
		expect(await screen.findByText("Mathematics")).toBeInTheDocument();
		expect(listSubstitutionNeeds).toHaveBeenCalledWith(7, "2026-11-12", "2026-11-12");
		expect(screen.getByText("Uncovered")).toBeInTheDocument();
		expect(screen.getByText("Half-day leave: check which periods are missed")).toBeInTheDocument();
		expect(screen.queryByRole("link", { name: "Assign cover" })).not.toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Approve" })).toBeInTheDocument();
	});

	it("links Assign cover to the Substitutions tab prefilled for the teacher and dates", async () => {
		grant("HR_LEAVE_MANAGE", "TIMETABLE_VIEW", "TIMETABLE_MANAGE");
		renderInbox();
		fireEvent.click(await screen.findByRole("button", { name: "Timetable cover" }));
		const link = await screen.findByRole("link", { name: "Assign cover" });
		expect(link).toHaveAttribute("href", "/academics?tab=substitutions&teacher=7&from=2026-11-12&to=2026-11-12");
	});

	it("says there are no timetabled periods for an empty result", async () => {
		grant("TIMETABLE_VIEW");
		vi.mocked(listSubstitutionNeeds).mockResolvedValue([]);
		renderInbox();
		fireEvent.click(await screen.findByRole("button", { name: "Timetable cover" }));
		await waitFor(() => expect(screen.getByText("No timetabled periods")).toBeInTheDocument());
	});
});
