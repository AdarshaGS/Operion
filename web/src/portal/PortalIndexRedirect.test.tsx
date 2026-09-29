import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { PortalIndexRedirect } from "./PortalIndexRedirect";
import * as AuthContext from "../auth/AuthContext";

function renderWith(mock: Partial<ReturnType<typeof AuthContext.useAuth>>) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue(mock as ReturnType<typeof AuthContext.useAuth>);
	render(
		<MemoryRouter initialEntries={["/portal"]}>
			<Routes>
				<Route path="/portal" element={<PortalIndexRedirect />} />
				<Route path="/portal/guardian" element={<div>Guardian dashboard</div>} />
				<Route path="/portal/teacher" element={<div>Teacher dashboard</div>} />
			</Routes>
		</MemoryRouter>,
	);
}

describe("PortalIndexRedirect", () => {
	it("goes straight to the guardian dashboard when that's the only context", () => {
		renderWith({ portalIdentity: { sessionType: "PORTAL", portalContexts: [{ type: "GUARDIAN", guardianId: 1, children: [], teacherPersonId: null }] } });
		expect(screen.getByText("Guardian dashboard")).toBeInTheDocument();
	});

	it("goes straight to the teacher dashboard when that's the only context", () => {
		renderWith({ portalIdentity: { sessionType: "PORTAL", portalContexts: [{ type: "TEACHER", guardianId: null, children: null, teacherPersonId: 9 }] } });
		expect(screen.getByText("Teacher dashboard")).toBeInTheDocument();
	});

	it("shows a switcher when both contexts are present", () => {
		renderWith({
			portalIdentity: {
				sessionType: "PORTAL",
				portalContexts: [
					{ type: "GUARDIAN", guardianId: 1, children: [], teacherPersonId: null },
					{ type: "TEACHER", guardianId: null, children: null, teacherPersonId: 9 },
				],
			},
		});
		expect(screen.getByText("Parent portal")).toBeInTheDocument();
		expect(screen.getByText("Teacher portal")).toBeInTheDocument();
	});

	it("shows a plain explainer when there are no contexts", () => {
		renderWith({ portalIdentity: { sessionType: "PORTAL", portalContexts: [] } });
		expect(screen.getByText(/doesn't have any portal access configured/)).toBeInTheDocument();
	});
});
