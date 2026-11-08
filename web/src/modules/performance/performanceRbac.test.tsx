import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";
import { PerformancePage } from "./PerformancePage";
import { StaffAppraisalPage } from "./StaffAppraisalPage";

vi.mock("../../api/performance", () => ({
	listCycleSummaries: vi.fn().mockResolvedValue([]),
	getCycleOverview: vi.fn(),
	listCycles: vi.fn().mockResolvedValue([{ id: 3, name: "Term 1", startDate: "2026-01-01", endDate: "2026-03-31", status: "OPEN" }]),
	getAppraisal: vi.fn().mockResolvedValue({
		cycle: { id: 3, name: "Term 1", startDate: "2026-01-01", endDate: "2026-03-31", status: "OPEN" },
		scorecard: { from: "2026-01-01", to: "2026-03-31", metrics: [], teachers: [] },
		feedback: [],
		goals: [],
		staff: {
			name: "Asha Nair", employeeCode: "EMP-1", designation: "Teacher", department: "Science",
			presencePercent: null, presenceSample: 0, presenceTarget: null, reviewStatus: "PENDING", managerRating: null,
		},
	}),
	giveFeedback: vi.fn(),
	addGoal: vi.fn(),
	updateGoal: vi.fn(),
	closeCycle: vi.fn(),
	createCycle: vi.fn(),
}));

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasAnyPermission: (required: string[]) => required.some((code) => codes.includes(code)),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

beforeEach(() => vi.restoreAllMocks());
afterEach(() => vi.restoreAllMocks());

describe("PerformancePage RBAC (KPI_MANAGE)", () => {
	function renderPage() {
		render(<MemoryRouter><PerformancePage /></MemoryRouter>);
	}

	it("offers cycle creation and the Metrics tab to a performance manager", async () => {
		grant("KPI_VIEW", "KPI_MANAGE");
		renderPage();
		expect(await screen.findByRole("tab", { name: "Metrics" })).toBeInTheDocument();
		expect(screen.getAllByRole("button", { name: "Create appraisal cycle" }).length).toBeGreaterThan(0);
		expect(screen.getByRole("button", { name: "Configure metrics" })).toBeInTheDocument();
	});

	it("hides cycle creation and the Metrics tab for a view-only caller", async () => {
		grant("KPI_VIEW");
		renderPage();
		expect(await screen.findByText("No appraisal cycles yet")).toBeInTheDocument();
		expect(screen.queryByRole("tab", { name: "Metrics" })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Create appraisal cycle" })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Configure metrics" })).not.toBeInTheDocument();
	});
});

describe("StaffAppraisalPage RBAC (KPI_REVIEW / KPI_MANAGE)", () => {
	function renderPage() {
		render(
			<MemoryRouter initialEntries={["/performance/staff/9?cycle=3"]}>
				<Routes>
					<Route path="/performance/staff/:personId" element={<StaffAppraisalPage />} />
				</Routes>
			</MemoryRouter>,
		);
	}

	it("shows Rate now, Add goal and Close cycle to a reviewer who is also a manager", async () => {
		grant("KPI_VIEW", "KPI_REVIEW", "KPI_MANAGE");
		renderPage();
		expect(await screen.findByRole("button", { name: "Rate now" })).toBeInTheDocument();
		expect(screen.getAllByRole("button", { name: "Add goal" }).length).toBeGreaterThan(0);
		expect(screen.getByRole("button", { name: "Close cycle" })).toBeInTheDocument();
	});

	it("hides every review and management action for a view-only caller", async () => {
		grant("KPI_VIEW");
		renderPage();
		expect(await screen.findByText("Review workflow")).toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Rate now" })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Add goal" })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Close cycle" })).not.toBeInTheDocument();
	});
});
