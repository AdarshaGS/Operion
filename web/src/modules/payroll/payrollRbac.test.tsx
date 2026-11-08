import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";
import { PayrollRunDetailPage } from "./PayrollRunDetailPage";
import { SalaryComponentsPanel } from "./SalaryComponentsPanel";

vi.mock("../../api/payroll", () => ({
	listSalaryComponents: vi.fn().mockResolvedValue([{ id: 1, name: "Basic", type: "EARNING", calculationBasis: "FLAT", status: "ACTIVE" }]),
	createSalaryComponent: vi.fn(),
	changeSalaryComponentStatus: vi.fn(),
	getPayrollRun: vi.fn().mockResolvedValue({
		id: 7, payYear: 2026, payMonth: 3, status: "DRAFT", supersedesRunId: null, payslipCount: 0,
		teacherTotals: { count: 0, gross: 0, deductions: 0, net: 0 }, staffTotals: { count: 0, gross: 0, deductions: 0, net: 0 }, payslips: [],
	}),
	finalizePayrollRun: vi.fn(),
	discardPayrollRun: vi.fn(),
	startPayrollCorrection: vi.fn(),
}));

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasAnyPermission: (required: string[]) => required.some((code) => codes.includes(code)),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

beforeEach(() => vi.restoreAllMocks());
afterEach(() => vi.restoreAllMocks());

describe("SalaryComponentsPanel RBAC (PAYROLL_MANAGE)", () => {
	it("shows Add component and Deactivate to a payroll manager", async () => {
		grant("PAYROLL_VIEW", "PAYROLL_MANAGE");
		render(<SalaryComponentsPanel />);
		expect(await screen.findByRole("button", { name: "Deactivate" })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Add component" })).toBeInTheDocument();
	});

	it("hides the write controls for a view-only caller but still lists components", async () => {
		grant("PAYROLL_VIEW");
		render(<SalaryComponentsPanel />);
		expect(await screen.findByText("Basic")).toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Deactivate" })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Add component" })).not.toBeInTheDocument();
	});
});

describe("PayrollRunDetailPage RBAC (PAYROLL_MANAGE)", () => {
	function renderPage() {
		render(
			<MemoryRouter initialEntries={["/payroll/runs/7"]}>
				<Routes>
					<Route path="/payroll/runs/:runId" element={<PayrollRunDetailPage />} />
				</Routes>
			</MemoryRouter>,
		);
	}

	it("shows Finalize and Discard on a draft run to a payroll manager", async () => {
		grant("PAYROLL_VIEW", "PAYROLL_MANAGE");
		renderPage();
		expect(await screen.findByRole("button", { name: "Finalize run" })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Discard draft" })).toBeInTheDocument();
	});

	it("hides Finalize and Discard for a view-only caller", async () => {
		grant("PAYROLL_VIEW");
		renderPage();
		expect(await screen.findByText(/Draft - open a payslip/)).toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Finalize run" })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Discard draft" })).not.toBeInTheDocument();
	});
});
