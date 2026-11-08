import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";

vi.mock("../../api/campuses", () => ({ listCampuses: vi.fn().mockResolvedValue([]) }));
vi.mock("../../api/items", () => ({ listItems: vi.fn().mockResolvedValue([]) }));
vi.mock("../../api/suppliers", () => ({ listSuppliers: vi.fn().mockResolvedValue([]) }));
vi.mock("../../api/purchaseOrders", () => ({
	createPurchaseOrder: vi.fn(),
	submitPurchaseOrder: vi.fn(),
	approvePurchaseOrder: vi.fn(),
	cancelPurchaseOrder: vi.fn(),
	receiveGoods: vi.fn(),
	recordPurchaseReturn: vi.fn(),
	listPurchaseOrders: vi.fn().mockResolvedValue([
		{ id: 3, supplierId: 1, campusId: 1, orderDate: "2026-01-01", expectedDate: "2026-01-10", status: "APPROVED", totalAmount: 100 },
	]),
	getPurchaseOrder: vi.fn().mockResolvedValue({
		id: 3,
		supplierId: 1,
		campusId: 1,
		orderDate: "2026-01-01",
		expectedDate: "2026-01-10",
		status: "APPROVED",
		totalAmount: 100,
		lines: [{ id: 11, itemId: 1, quantity: 5, unitCost: 20, quantityReceived: 5, quantityReturned: 0 }],
	}),
}));

import { PurchaseOrderDetailPage } from "./PurchaseOrderDetailPage";
import { PurchaseOrdersPanel } from "./PurchaseOrdersPanel";

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasAnyPermission: (required: string[]) => required.some((code) => codes.includes(code)),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

function renderDetail() {
	render(
		<MemoryRouter initialEntries={["/purchase/orders/3"]}>
			<Routes>
				<Route path="/purchase/orders/:orderId" element={<PurchaseOrderDetailPage />} />
			</Routes>
		</MemoryRouter>,
	);
}

describe("Purchase permission gating", () => {
	afterEach(() => vi.restoreAllMocks());

	it("shows New purchase order to a caller holding PURCHASE_MANAGE", async () => {
		grant("PURCHASE_VIEW", "PURCHASE_MANAGE");
		render(<MemoryRouter><PurchaseOrdersPanel /></MemoryRouter>);
		expect(await screen.findByRole("button", { name: /new purchase order/i })).toBeInTheDocument();
	});

	it("hides New purchase order from a view-only caller", async () => {
		grant("PURCHASE_VIEW");
		render(<MemoryRouter><PurchaseOrdersPanel /></MemoryRouter>);
		expect(await screen.findByText("Purchase orders")).toBeInTheDocument();
		expect(screen.queryByRole("button", { name: /new purchase order/i })).not.toBeInTheDocument();
	});

	it("shows order actions, Return and its column to a caller holding PURCHASE_MANAGE", async () => {
		grant("PURCHASE_VIEW", "PURCHASE_MANAGE");
		renderDetail();
		expect(await screen.findByRole("button", { name: /receive goods/i })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /^cancel$/i })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /^return$/i })).toBeInTheDocument();
		expect(screen.getAllByRole("columnheader")).toHaveLength(6);
	});

	it("drops the order actions and the Return column for a view-only caller", async () => {
		grant("PURCHASE_VIEW");
		renderDetail();
		expect(await screen.findByText("Line items")).toBeInTheDocument();
		expect(screen.queryByRole("button", { name: /receive goods/i })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: /^cancel$/i })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: /^return$/i })).not.toBeInTheDocument();
		expect(screen.getAllByRole("columnheader")).toHaveLength(5);
	});
});
