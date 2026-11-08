import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";

const VEHICLE = { id: 1, campusId: 1, registrationNumber: "KA-01-1234", vehicleType: "BUS", capacity: 40, driverPersonId: null, attendantPersonId: null, status: "ACTIVE" };
const ROUTE = { id: 1, campusId: 1, name: "Route 12 - North", code: "R12", vehicleId: null, status: "ACTIVE" };

vi.mock("../../api/campuses", () => ({ listCampuses: vi.fn(async () => [{ id: 1, name: "Main Campus" }]) }));
vi.mock("../../api/vehicles", () => ({
	listVehicles: vi.fn(async () => [VEHICLE]),
	createVehicle: vi.fn(),
	changeVehicleStatus: vi.fn(),
}));
vi.mock("../../api/transportRoutes", () => ({
	listRoutes: vi.fn(async () => [ROUTE]),
	createRoute: vi.fn(),
}));

import { RoutesPanel } from "./RoutesPanel";
import { VehiclesPanel } from "./VehiclesPanel";

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasAnyPermission: (required: string[]) => required.some((code) => codes.includes(code)),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

function renderInRouter(node: React.ReactNode) {
	return render(<MemoryRouter>{node}</MemoryRouter>);
}

afterEach(() => vi.restoreAllMocks());

describe("VehiclesPanel permissions", () => {
	it("shows Add vehicle and an editable status select with TRANSPORT_VEHICLE_MANAGE", async () => {
		grant("TRANSPORT_VEHICLE_MANAGE");
		renderInRouter(<VehiclesPanel />);
		await screen.findByText("KA-01-1234");
		expect(screen.getByRole("button", { name: "Add vehicle" })).toBeInTheDocument();
		expect(screen.getByRole("combobox")).not.toHaveAttribute("aria-disabled", "true");
	});

	it("hides Add vehicle and disables the status select without it", async () => {
		grant("TRANSPORT_VIEW");
		renderInRouter(<VehiclesPanel />);
		await screen.findByText("KA-01-1234");
		expect(screen.queryByRole("button", { name: "Add vehicle" })).not.toBeInTheDocument();
		expect(screen.getByRole("combobox")).toHaveAttribute("aria-disabled", "true");
	});
});

describe("RoutesPanel permissions", () => {
	it("shows Add route with TRANSPORT_ROUTE_MANAGE", async () => {
		grant("TRANSPORT_ROUTE_MANAGE");
		renderInRouter(<RoutesPanel />);
		await screen.findByText("Route 12 - North");
		expect(screen.getByRole("button", { name: "Add route" })).toBeInTheDocument();
	});

	it("hides Add route without it (TRANSPORT_VEHICLE_MANAGE is not enough)", async () => {
		grant("TRANSPORT_VIEW", "TRANSPORT_VEHICLE_MANAGE");
		renderInRouter(<RoutesPanel />);
		await screen.findByText("Route 12 - North");
		expect(screen.queryByRole("button", { name: "Add route" })).not.toBeInTheDocument();
	});
});
