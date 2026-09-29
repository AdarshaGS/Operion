import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { AdminOnlyRoute } from "./AdminOnlyRoute";
import * as AuthContext from "./AuthContext";

function renderWith(mock: Partial<ReturnType<typeof AuthContext.useAuth>>) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue(mock as ReturnType<typeof AuthContext.useAuth>);
	render(
		<MemoryRouter initialEntries={["/dashboard"]}>
			<Routes>
				<Route element={<AdminOnlyRoute />}>
					<Route path="/dashboard" element={<div>Admin dashboard</div>} />
				</Route>
				<Route path="/portal" element={<div>Portal landing</div>} />
			</Routes>
		</MemoryRouter>,
	);
}

describe("AdminOnlyRoute", () => {
	it("renders nothing until permissions have loaded", () => {
		renderWith({ permissionsLoaded: false, portalIdentity: null });
		expect(screen.queryByText(/Admin dashboard|Portal landing/)).not.toBeInTheDocument();
	});

	it("renders the admin route for an ADMIN session", () => {
		renderWith({ permissionsLoaded: true, portalIdentity: { sessionType: "ADMIN", portalContexts: [] } });
		expect(screen.getByText("Admin dashboard")).toBeInTheDocument();
	});

	it("redirects a PORTAL session to /portal", () => {
		renderWith({ permissionsLoaded: true, portalIdentity: { sessionType: "PORTAL", portalContexts: [] } });
		expect(screen.getByText("Portal landing")).toBeInTheDocument();
	});
});
