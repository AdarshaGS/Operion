import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";

vi.mock("../../api/reports", () => ({
	getReport: vi.fn(async () => ({
		id: 5,
		name: "Fee dues",
		description: null,
		sqlQuery: "SELECT 1",
		status: "PUBLISHED",
		createdBy: 1,
		parameters: [],
		columns: [],
	})),
	runReport: vi.fn(),
	exportReport: vi.fn(),
	updateReport: vi.fn(),
	publishReport: vi.fn(),
	archiveReport: vi.fn(),
	duplicateReport: vi.fn(),
	shareReport: vi.fn(),
}));

import { ReportDetailPage } from "./ReportDetailPage";

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasAnyPermission: (required: string[]) => required.some((code) => codes.includes(code)),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

function renderPage() {
	return render(
		<MemoryRouter initialEntries={["/reports/5"]}>
			<Routes>
				<Route path="/reports/:reportId" element={<ReportDetailPage />} />
			</Routes>
		</MemoryRouter>,
	);
}

afterEach(() => vi.restoreAllMocks());

describe("ReportDetailPage permissions", () => {
	it("enables Export as CSV with REPORT_EXPORT", async () => {
		grant("REPORT_EXPORT");
		renderPage();

		expect(await screen.findByRole("button", { name: "Export as CSV" })).toBeEnabled();
	});

	it("disables Export as CSV without REPORT_EXPORT while Run stays available", async () => {
		grant("REPORT_VIEW");
		renderPage();

		expect(await screen.findByRole("button", { name: "Export as CSV" })).toBeDisabled();
		expect(screen.getByRole("button", { name: "Run" })).toBeEnabled();
	});
});
