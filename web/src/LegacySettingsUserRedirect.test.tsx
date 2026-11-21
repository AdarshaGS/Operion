import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { LegacySettingsUserRedirect } from "./App";

function Where() {
	return <div>at {useLocation().pathname}</div>;
}

describe("LegacySettingsUserRedirect", () => {
	it("sends an old /settings/users/:userId bookmark to /settings/members/:userId", async () => {
		render(
			<MemoryRouter initialEntries={["/settings/users/42"]}>
				<Routes>
					<Route path="/settings/users/:userId" element={<LegacySettingsUserRedirect />} />
					<Route path="/settings/members/:userId" element={<Where />} />
				</Routes>
			</MemoryRouter>,
		);
		expect(await screen.findByText("at /settings/members/42")).toBeInTheDocument();
	});
});
