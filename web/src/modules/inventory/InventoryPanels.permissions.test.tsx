import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";

const ASSET = {
	id: 1,
	itemId: 1,
	itemName: "Laptop",
	campusId: 1,
	assetTag: "LAPTOP-0001",
	assignedPersonId: null,
	assignedPersonName: null,
	assignedLocation: null,
	purchaseDate: null,
	warrantyExpiryDate: null,
	status: "ACTIVE",
};
const SUPPLIER = { id: 1, name: "Acme Supplies", contactPerson: null, phone: null, email: null, address: null, status: "ACTIVE" };

vi.mock("../../api/campuses", () => ({ listCampuses: vi.fn(async () => [{ id: 1, name: "Main Campus" }]) }));
vi.mock("../../api/items", () => ({ listItems: vi.fn(async () => [{ id: 1, name: "Laptop" }]) }));
vi.mock("../../api/persons", () => ({ listPersons: vi.fn(async () => []) }));
vi.mock("../../api/staffProfiles", () => ({ listStaffProfiles: vi.fn(async () => []) }));
vi.mock("../../api/inventoryAssets", () => ({
	listInventoryAssets: vi.fn(async () => [ASSET]),
	createInventoryAsset: vi.fn(),
	changeInventoryAssetStatus: vi.fn(),
	assignInventoryAsset: vi.fn(),
}));
vi.mock("../../api/suppliers", () => ({
	listSuppliers: vi.fn(async () => [SUPPLIER]),
	createSupplier: vi.fn(),
	changeSupplierStatus: vi.fn(),
}));

import { AssetsPanel } from "./AssetsPanel";
import { SuppliersPanel } from "./SuppliersPanel";

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasAnyPermission: (required: string[]) => required.some((code) => codes.includes(code)),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

afterEach(() => vi.restoreAllMocks());

describe("AssetsPanel permissions", () => {
	it("shows add, assign and an editable status select with INVENTORY_ASSET_MANAGE", async () => {
		grant("INVENTORY_ASSET_MANAGE");
		render(<AssetsPanel />);
		await screen.findByText("LAPTOP-0001");
		expect(screen.getByRole("button", { name: "Add asset" })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Assign" })).toBeInTheDocument();
		expect(screen.getByRole("combobox", { name: "" })).not.toHaveAttribute("aria-disabled", "true");
	});

	it("hides add and assign and disables the status select without it", async () => {
		grant("INVENTORY_VIEW");
		render(<AssetsPanel />);
		await screen.findByText("LAPTOP-0001");
		expect(screen.queryByRole("button", { name: "Add asset" })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Assign" })).not.toBeInTheDocument();
		await waitFor(() => expect(screen.getByRole("combobox", { name: "" })).toHaveAttribute("aria-disabled", "true"));
	});
});

describe("SuppliersPanel permissions", () => {
	it("shows add and deactivate with INVENTORY_SUPPLIER_MANAGE", async () => {
		grant("INVENTORY_SUPPLIER_MANAGE");
		render(<SuppliersPanel />);
		await screen.findByText("Acme Supplies");
		expect(screen.getByRole("button", { name: "Add supplier" })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Deactivate" })).toBeInTheDocument();
	});

	it("hides add and deactivate without it", async () => {
		grant("INVENTORY_VIEW");
		render(<SuppliersPanel />);
		await screen.findByText("Acme Supplies");
		expect(screen.queryByRole("button", { name: "Add supplier" })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Deactivate" })).not.toBeInTheDocument();
	});
});
