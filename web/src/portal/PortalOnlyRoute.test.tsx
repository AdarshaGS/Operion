import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { PortalOnlyRoute } from "./PortalOnlyRoute";
import * as AuthContext from "../auth/AuthContext";

function renderWith(mock: Partial<ReturnType<typeof AuthContext.useAuth>>) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue(mock as ReturnType<typeof AuthContext.useAuth>);
	render(
		<MemoryRouter initialEntries={["/portal"]}>
			<Routes>
				<Route element={<PortalOnlyRoute />}>
					<Route path="/portal" element={<div>Portal landing</div>} />
				</Route>
				<Route path="/" element={<div>Admin index</div>} />
			</Routes>
		</MemoryRouter>,
	);
}

describe("PortalOnlyRoute", () => {
	it("renders nothing until permissions have loaded", () => {
		renderWith({ permissionsLoaded: false, portalIdentity: null });
		expect(screen.queryByText(/Portal landing|Admin index/)).not.toBeInTheDocument();
	});

	it("renders the portal route for a PORTAL session", () => {
		renderWith({ permissionsLoaded: true, portalIdentity: { sessionType: "PORTAL", portalContexts: [] } });
		expect(screen.getByText("Portal landing")).toBeInTheDocument();
	});

	it("redirects an ADMIN session away to /", () => {
		renderWith({ permissionsLoaded: true, portalIdentity: { sessionType: "ADMIN", portalContexts: [] } });
		expect(screen.getByText("Admin index")).toBeInTheDocument();
	});
});
