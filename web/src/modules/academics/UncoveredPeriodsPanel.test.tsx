import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";
import { ApiError } from "../../api/client";

const SLOT = {
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
	leaveRequestId: 1,
	absentTeacherPersonId: 7,
	absentTeacherName: "Asha Rao",
	campusId: 1,
};

const SUGGESTIONS = [
	{ entryId: 5, date: "2026-11-12", periodLabel: "Period 2", sectionName: "A", subjectName: "Mathematics", absentTeacherName: "Asha Rao", proposed: { personId: 9, name: "Ravi Iyer", rank: "Teaches the section" }, reasonIfNone: null },
	{ entryId: 6, date: "2026-11-12", periodLabel: "Period 4", sectionName: "B", subjectName: "Science", absentTeacherName: "Asha Rao", proposed: null, reasonIfNone: "Every teacher is busy" },
];

vi.mock("../../api/timetable", () => ({
	listUncoveredSlots: vi.fn(),
	listCoverSuggestions: vi.fn(),
	assignSubstitution: vi.fn(),
}));

import { assignSubstitution, listCoverSuggestions, listUncoveredSlots } from "../../api/timetable";
import { UncoveredPeriodsPanel } from "./UncoveredPeriodsPanel";

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasPermission: (code: string) => codes.includes(code),
		hasAnyPermission: (required: string[]) => required.some((code) => codes.includes(code)),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

beforeEach(() => {
	vi.mocked(listUncoveredSlots).mockReset().mockResolvedValue([SLOT]);
	vi.mocked(listCoverSuggestions).mockReset().mockResolvedValue(SUGGESTIONS);
	vi.mocked(assignSubstitution).mockReset().mockResolvedValue({} as never);
});
afterEach(() => vi.restoreAllMocks());

describe("UncoveredPeriodsPanel", () => {
	it("lists the slots and hides Auto-suggest cover without TIMETABLE_MANAGE", async () => {
		grant("TIMETABLE_VIEW");
		render(<UncoveredPeriodsPanel />);
		expect(await screen.findByText("Mathematics")).toBeInTheDocument();
		expect(screen.getByText("Uncovered")).toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Auto-suggest cover" })).not.toBeInTheDocument();
	});

	it("shows an empty state and an error state", async () => {
		grant("TIMETABLE_VIEW");
		vi.mocked(listUncoveredSlots).mockResolvedValueOnce([]);
		const { unmount } = render(<UncoveredPeriodsPanel />);
		expect(await screen.findByText(/No periods are affected/)).toBeInTheDocument();
		unmount();

		vi.mocked(listUncoveredSlots).mockRejectedValueOnce(new ApiError(500, "boom"));
		render(<UncoveredPeriodsPanel />);
		expect(await screen.findByText("boom")).toBeInTheDocument();
	});

	it("disables Auto-suggest cover for a range over 14 days", async () => {
		grant("TIMETABLE_VIEW", "TIMETABLE_MANAGE");
		render(<UncoveredPeriodsPanel />);
		await screen.findByText("Mathematics");
		fireEvent.change(screen.getByLabelText("To"), { target: { value: "2099-01-01" } });
		expect(screen.getByRole("button", { name: "Auto-suggest cover" })).toBeDisabled();
		expect(screen.getByText(/Auto-suggest works on at most 14 days/)).toBeInTheDocument();
	});

	it("previews suggestions and does not assign anything until Confirm, then reports each row", async () => {
		grant("TIMETABLE_VIEW", "TIMETABLE_MANAGE");
		vi.mocked(assignSubstitution).mockRejectedValueOnce(new ApiError(400, "Ravi Iyer is no longer free"));
		render(<UncoveredPeriodsPanel />);
		await screen.findByText("Mathematics");

		fireEvent.click(screen.getByRole("button", { name: "Auto-suggest cover" }));
		const dialog = await screen.findByRole("dialog");
		expect(listCoverSuggestions).toHaveBeenCalledTimes(1);
		expect(within(dialog).getByText("Every teacher is busy")).toBeInTheDocument();
		expect(assignSubstitution).not.toHaveBeenCalled();

		fireEvent.click(within(dialog).getByRole("button", { name: "Cancel" }));
		await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
		expect(assignSubstitution).not.toHaveBeenCalled();

		fireEvent.click(screen.getByRole("button", { name: "Auto-suggest cover" }));
		const reopened = await screen.findByRole("dialog");
		expect(assignSubstitution).not.toHaveBeenCalled();
		fireEvent.click(within(reopened).getByRole("button", { name: "Confirm" }));

		await within(reopened).findByText("Failed: Ravi Iyer is no longer free");
		expect(assignSubstitution).toHaveBeenCalledTimes(1);
		expect(assignSubstitution).toHaveBeenCalledWith(expect.objectContaining({ entryId: 5, date: "2026-11-12", substituteTeacherPersonId: 9 }));

		// A retry sends only the row that has not succeeded.
		fireEvent.click(within(reopened).getByRole("button", { name: "Confirm" }));
		await within(reopened).findByText("Assigned");
		expect(assignSubstitution).toHaveBeenCalledTimes(2);
	});
});
