import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";

vi.mock("../../api/reports", () => ({
	listReports: vi.fn(async () => []),
	createReport: vi.fn(),
	seedStandardReports: vi.fn(),
}));

import { ReportsPanel } from "./ReportsPanel";

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		session: { userId: 1 },
		hasAnyPermission: (required: string[]) => required.some((code) => codes.includes(code)),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

function renderPanel() {
	return render(
		<MemoryRouter>
			<ReportsPanel />
		</MemoryRouter>,
	);
}

afterEach(() => vi.restoreAllMocks());

describe("ReportsPanel permissions", () => {
	it("enables Create report and shows Seed standard reports for REPORT_CREATE + REPORT_MANAGE", async () => {
		grant("REPORT_CREATE", "REPORT_MANAGE");
		renderPanel();

		expect(await screen.findByRole("button", { name: "Create report" })).toBeEnabled();
		expect(screen.getByRole("button", { name: "Seed standard reports" })).toBeInTheDocument();
	});

	it("disables Create report and hides Seed standard reports for a view-only caller", async () => {
		grant("REPORT_VIEW");
		renderPanel();

		expect(await screen.findByRole("button", { name: "Create report" })).toBeDisabled();
		expect(screen.queryByRole("button", { name: "Seed standard reports" })).not.toBeInTheDocument();
	});
});
