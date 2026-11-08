import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { SetupChecklist } from "../../api/dashboard";
import * as AuthContext from "../../auth/AuthContext";
import { SetupProgress } from "./SetupProgress";

const NOTHING_DONE: SetupChecklist = {
	rolesConfigured: false,
	membersAdded: false,
	academicSetupConfigured: false,
	studentsAdded: false,
	feesConfigured: false,
	attendanceStarted: false,
	institutionProfileConfigured: false,
	academicYearSet: false,
	classesAndSectionsCreated: false,
	subjectsMapped: false,
	admissionsCompleted: false,
	studentRecordsExist: false,
	examCreated: false,
	marksApproved: false,
	libraryStocked: false,
	academicYearClosed: false,
};

/** Shows where the router currently is, so a test can see what a click navigated to and with what state. */
function LocationProbe() {
	const location = useLocation();
	return <div data-testid="location">{`${location.pathname} ${JSON.stringify(location.state)}`}</div>;
}

function renderCard(isPathEnabled: (path: string) => boolean, checklist: SetupChecklist = NOTHING_DONE) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({ isPathEnabled } as ReturnType<typeof AuthContext.useAuth>);
	render(
		<MemoryRouter>
			<SetupProgress checklist={checklist} onDismiss={() => undefined} />
			<LocationProbe />
		</MemoryRouter>,
	);
}

describe("SetupProgress", () => {
	beforeEach(() => localStorage.clear());
	afterEach(() => vi.restoreAllMocks());

	it("lists Team members & roles right after Academic year creation", () => {
		renderCard(() => true);
		const labels = screen.getAllByText(/^(Academic year creation|Team members & roles|Class & section creation)$/).map((node) => node.textContent);
		expect(labels).toEqual(["Academic year creation", "Team members & roles", "Class & section creation"]);
		expect(screen.getByText(/of 12 setup tasks complete/)).toBeInTheDocument();
	});

	it("puts fee structure before the student steps and leaves fee collection out", () => {
		renderCard(() => true);
		const order = [
			"Institution setup",
			"Academic year creation",
			"Team members & roles",
			"Class & section creation",
			"Subject configuration",
			"Fee structure configuration",
			"Student admissions",
			"Student record management",
			"Attendance management",
			"Examination creation",
			"Marks entry",
			"Library administration",
			"Academic year closure",
		];
		const shown = screen
			.getAllByRole("paragraph")
			.map((node) => node.textContent ?? "")
			.filter((text) => order.includes(text));
		expect(shown).toEqual(order);
		expect(screen.queryByText("Fee collection")).not.toBeInTheDocument();
	});

	it("starts a new Foundation caption for the later year-closure step", () => {
		renderCard(() => true);
		expect(screen.getAllByText("Foundation")).toHaveLength(2);
		expect(screen.getAllByText("Academic & students")).toHaveLength(1);
		expect(screen.getAllByText("Daily operations")).toHaveLength(1);
	});

	it("marks the dashboard as the origin when a step row is clicked", () => {
		renderCard(() => true);
		fireEvent.click(screen.getByText("Subject configuration"));
		expect(screen.getByTestId("location")).toHaveTextContent('/academics {"from":"dashboard"}');
	});

	it("marks the dashboard as the origin when the call-to-action is clicked", () => {
		renderCard(() => true);
		fireEvent.click(screen.getByRole("button", { name: "Set up school" }));
		expect(screen.getByTestId("location")).toHaveTextContent('/setup/structure {"from":"dashboard"}');
	});

	it("numbers the shown steps 1..N with no gaps when fees and library are switched off", () => {
		renderCard((path) => !path.startsWith("/fees") && path !== "/library");
		// 13 steps minus fee structure and library = 11, the last being year closure.
		for (let number = 1; number <= 11; number++) {
			expect(screen.getByText(String(number), { selector: "div" })).toBeInTheDocument();
		}
		expect(screen.queryByText("12", { selector: "div" })).not.toBeInTheDocument();
		expect(screen.queryByText("Fee structure configuration")).not.toBeInTheDocument();
		expect(screen.getByText(/Steps 1.11 follow one academic year/)).toBeInTheDocument();
	});

	it("collapses to a one-line summary and remembers the choice", () => {
		renderCard(() => true);
		fireEvent.click(screen.getByRole("button", { name: /Next setup step/ }));
		expect(screen.getByText("Setup guide - 0 of 12 complete")).toBeInTheDocument();
		expect(screen.queryByText("Institution setup")).not.toBeInTheDocument();
		expect(screen.getByRole("progressbar")).toBeInTheDocument();
		expect(localStorage.getItem("operion.dashboard.setupGuideExpanded")).toBe("0");
	});

	it("starts collapsed when the viewer last collapsed it", () => {
		localStorage.setItem("operion.dashboard.setupGuideExpanded", "0");
		renderCard(() => true);
		expect(screen.getByText("Setup guide - 0 of 12 complete")).toBeInTheDocument();
	});

	it("still works when localStorage throws", () => {
		vi.spyOn(localStorage, "getItem").mockImplementation(() => {
			throw new Error("storage blocked");
		});
		vi.spyOn(localStorage, "setItem").mockImplementation(() => {
			throw new Error("storage blocked");
		});
		renderCard(() => true);
		expect(screen.getByText("Institution setup")).toBeInTheDocument();
		fireEvent.click(screen.getByRole("button", { name: /Next setup step/ }));
		expect(screen.getByText("Setup guide - 0 of 12 complete")).toBeInTheDocument();
	});
});
