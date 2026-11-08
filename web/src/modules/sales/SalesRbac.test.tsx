import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";

vi.mock("../../api/campuses", () => ({ listCampuses: vi.fn().mockResolvedValue([]) }));
vi.mock("../../api/customers", () => ({ listCustomers: vi.fn().mockResolvedValue([]) }));
vi.mock("../../api/items", () => ({ listItems: vi.fn().mockResolvedValue([]) }));
vi.mock("../../api/sales", () => ({
	createSale: vi.fn(),
	recordSalePayment: vi.fn(),
	listSales: vi.fn().mockResolvedValue([
		{ id: 7, customerId: 1, campusId: 1, receiptNumber: "RCPT-7", saleDate: "2026-01-01", totalAmount: 100, amountPaid: 0, status: "COMPLETED" },
	]),
	getSale: vi.fn().mockResolvedValue({
		id: 7,
		customerId: 1,
		campusId: 1,
		receiptNumber: "RCPT-7",
		saleDate: "2026-01-01",
		totalAmount: 100,
		amountPaid: 40,
		status: "PARTIALLY_PAID",
		lines: [],
		payments: [],
	}),
}));

import { SaleDetailPage } from "./SaleDetailPage";
import { SalesPanel } from "./SalesPanel";

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasAnyPermission: (required: string[]) => required.some((code) => codes.includes(code)),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

function renderDetail() {
	render(
		<MemoryRouter initialEntries={["/sales/7"]}>
			<Routes>
				<Route path="/sales/:saleId" element={<SaleDetailPage />} />
			</Routes>
		</MemoryRouter>,
	);
}

describe("Sales permission gating", () => {
	afterEach(() => vi.restoreAllMocks());

	it("shows New sale to a caller holding SALES_MANAGE", async () => {
		grant("SALES_VIEW", "SALES_MANAGE");
		render(<MemoryRouter><SalesPanel /></MemoryRouter>);
		expect(await screen.findByText("RCPT-7")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /new sale/i })).toBeInTheDocument();
	});

	it("hides New sale from a view-only caller but still lists the sales", async () => {
		grant("SALES_VIEW");
		render(<MemoryRouter><SalesPanel /></MemoryRouter>);
		expect(await screen.findByText("RCPT-7")).toBeInTheDocument();
		expect(screen.queryByRole("button", { name: /new sale/i })).not.toBeInTheDocument();
	});

	it("shows Record payment on an unpaid sale to a caller holding SALES_MANAGE", async () => {
		grant("SALES_VIEW", "SALES_MANAGE");
		renderDetail();
		expect(await screen.findByRole("button", { name: /record payment/i })).toBeInTheDocument();
	});

	it("hides Record payment from a view-only caller", async () => {
		grant("SALES_VIEW");
		renderDetail();
		expect(await screen.findByText(/outstanding/i)).toBeInTheDocument();
		expect(screen.queryByRole("button", { name: /record payment/i })).not.toBeInTheDocument();
	});
});
