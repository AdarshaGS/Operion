import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { IndexRedirect } from "./IndexRedirect";
import * as AuthContext from "./AuthContext";

function renderAt(mock: Partial<ReturnType<typeof AuthContext.useAuth>>) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue(mock as ReturnType<typeof AuthContext.useAuth>);
	render(
		<MemoryRouter initialEntries={["/"]}>
			<Routes>
				<Route path="/" element={<IndexRedirect />} />
				<Route path="/portal" element={<div>Portal landing</div>} />
				<Route path="/dashboard" element={<div>Dashboard</div>} />
				<Route path="/students" element={<div>Students</div>} />
			</Routes>
		</MemoryRouter>,
	);
}

describe("IndexRedirect", () => {
	it("renders nothing until permissions have loaded", () => {
		renderAt({ permissionsLoaded: false, hasPermission: () => false, portalIdentity: null });
		expect(screen.queryByText(/Dashboard|Students|Portal landing/)).not.toBeInTheDocument();
	});

	it("sends a PORTAL session to /portal regardless of permissions", () => {
		renderAt({
			permissionsLoaded: true,
			hasPermission: () => true,
			portalIdentity: { sessionType: "PORTAL", portalContexts: [] },
		});
		expect(screen.getByText("Portal landing")).toBeInTheDocument();
	});

	it("sends an ADMIN session with ORGANISATION_MANAGE to /dashboard", () => {
		renderAt({
			permissionsLoaded: true,
			hasPermission: (code: string) => code === "ORGANISATION_MANAGE",
			portalIdentity: { sessionType: "ADMIN", portalContexts: [] },
		});
		expect(screen.getByText("Dashboard")).toBeInTheDocument();
	});

	it("sends an ADMIN session without ORGANISATION_MANAGE to /students", () => {
		renderAt({ permissionsLoaded: true, hasPermission: () => false, portalIdentity: { sessionType: "ADMIN", portalContexts: [] } });
		expect(screen.getByText("Students")).toBeInTheDocument();
	});
});
