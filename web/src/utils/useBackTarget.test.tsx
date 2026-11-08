import { renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { FROM_DASHBOARD_STATE, useBackTarget } from "./useBackTarget";

function renderTarget(state?: unknown) {
	const wrapper = ({ children }: { children: ReactNode }) => (
		<MemoryRouter initialEntries={[{ pathname: "/fees/setup", state }]}>{children}</MemoryRouter>
	);
	return renderHook(() => useBackTarget("/fees"), { wrapper }).result.current;
}

describe("useBackTarget", () => {
	it("goes to the dashboard when the screen was opened from the dashboard setup card", () => {
		expect(renderTarget(FROM_DASHBOARD_STATE)).toEqual({ path: "/dashboard", fromDashboard: true });
	});

	it("uses the screen's own back target when it was opened any other way", () => {
		expect(renderTarget(undefined)).toEqual({ path: "/fees", fromDashboard: false });
		expect(renderTarget(null)).toEqual({ path: "/fees", fromDashboard: false });
		expect(renderTarget({ from: "somewhere-else" })).toEqual({ path: "/fees", fromDashboard: false });
		expect(renderTarget({ blockedMessage: "unrelated state" })).toEqual({ path: "/fees", fromDashboard: false });
	});
});
