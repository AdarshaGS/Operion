import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";

vi.mock("../../api/departments", () => ({
	listDepartments: vi.fn(),
	createDepartment: vi.fn(),
	changeDepartmentStatus: vi.fn(),
}));

import { listDepartments } from "../../api/departments";
import { DepartmentsPanel } from "./DepartmentsPanel";

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasAnyPermission: (required: string[]) => required.some((code) => codes.includes(code)),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

describe("DepartmentsPanel RBAC", () => {
	beforeEach(() => {
		vi.mocked(listDepartments).mockResolvedValue([{ id: 1, name: "Science", status: "ACTIVE" }]);
	});
	afterEach(() => vi.restoreAllMocks());

	it("shows the add and deactivate controls with ORGANISATION_MANAGE", async () => {
		grant("ORGANISATION_MANAGE");
		render(<DepartmentsPanel />);
		expect(await screen.findByText("Science")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /add department/i })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Deactivate" })).toBeInTheDocument();
	});

	it("hides the add and deactivate controls but keeps the list without it", async () => {
		grant("STUDENT_VIEW");
		render(<DepartmentsPanel />);
		expect(await screen.findByText("Science")).toBeInTheDocument();
		await waitFor(() => expect(listDepartments).toHaveBeenCalled());
		expect(screen.queryByRole("button", { name: /add department/i })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Deactivate" })).not.toBeInTheDocument();
	});
});
