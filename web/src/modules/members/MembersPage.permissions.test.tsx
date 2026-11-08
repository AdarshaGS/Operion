import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";

vi.mock("../../api/memberships", () => ({
	listMemberships: vi.fn(async () => []),
	revokeMembership: vi.fn(),
	createMembership: vi.fn(),
}));
vi.mock("../../api/roles", () => ({ listRoles: vi.fn(async () => []) }));
vi.mock("../../api/campuses", () => ({ listCampuses: vi.fn(async () => []) }));
vi.mock("../../api/departments", () => ({ listDepartments: vi.fn(async () => []) }));
vi.mock("../../api/designations", () => ({ listDesignations: vi.fn(async () => []) }));
vi.mock("../../api/staffProfiles", () => ({ createStaffProfile: vi.fn() }));

import { MemberCreatePage } from "./MemberCreatePage";
import { MembersPage } from "./MembersPage";

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasAnyPermission: (required: string[]) => required.some((code) => codes.includes(code)),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

function renderPage(initialPath = "/members") {
	return render(
		<MemoryRouter initialEntries={[initialPath]}>
			<Routes>
				<Route path="/members" element={<MembersPage />} />
				<Route path="/members/new" element={<MemberCreatePage />} />
			</Routes>
		</MemoryRouter>,
	);
}

afterEach(() => vi.restoreAllMocks());

describe("MembersPage permissions", () => {
	it("shows the Add member button to a MEMBERSHIP_MANAGE holder", async () => {
		grant("MEMBERSHIP_VIEW", "MEMBERSHIP_MANAGE");
		renderPage();

		expect(await screen.findByRole("button", { name: "Add member" })).toBeInTheDocument();
	});

	it("hides the Add member button from a view-only caller", async () => {
		grant("MEMBERSHIP_VIEW");
		renderPage();

		await screen.findByRole("heading", { name: "Members" });
		await waitFor(() => expect(screen.queryByRole("button", { name: "Add member" })).not.toBeInTheDocument());
	});

	it("opens the Add member page (not a dialog) from the button", async () => {
		grant("MEMBERSHIP_VIEW", "MEMBERSHIP_MANAGE");
		renderPage();

		fireEvent.click(await screen.findByRole("button", { name: "Add member" }));

		expect(await screen.findByRole("heading", { name: "Add member" })).toBeInTheDocument();
		expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
	});

	it("shows the Add member page at /members/new only to a MEMBERSHIP_MANAGE holder", async () => {
		grant("MEMBERSHIP_VIEW", "MEMBERSHIP_MANAGE");
		const { unmount } = renderPage("/members/new");
		expect(await screen.findByRole("heading", { name: "Add member" })).toBeInTheDocument();
		unmount();

		grant("MEMBERSHIP_VIEW");
		renderPage("/members/new");
		expect(await screen.findByRole("heading", { name: "Members" })).toBeInTheDocument();
		expect(screen.queryByRole("heading", { name: "Add member" })).not.toBeInTheDocument();
	});
});

describe("Add member page role selection", () => {
	it("toggles a role off again when it is clicked a second time", async () => {
		const roles = await import("../../api/roles");
		vi.mocked(roles.listRoles).mockResolvedValue([
			{ id: 7, name: "Teacher", status: "ACTIVE" },
		] as Awaited<ReturnType<typeof roles.listRoles>>);
		grant("MEMBERSHIP_VIEW", "MEMBERSHIP_MANAGE");
		renderPage("/members/new");

		fireEvent.mouseDown(await screen.findByRole("combobox", { name: /Role\(s\)/ }));
		const option = await screen.findByRole("option", { name: "Teacher" });
		fireEvent.click(option);
		await waitFor(() => expect(screen.getByRole("option", { name: "Teacher" })).toHaveAttribute("aria-selected", "true"));
		fireEvent.click(screen.getByRole("option", { name: "Teacher" }));
		await waitFor(() => expect(screen.getByRole("option", { name: "Teacher" })).toHaveAttribute("aria-selected", "false"));
	});
});
