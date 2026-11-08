import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";
import { JobApplicationsPanel } from "./JobApplicationsPanel";
import { StaffCreatePage } from "./StaffCreatePage";

vi.mock("../../api/jobApplications", () => ({
	listJobApplications: vi.fn().mockResolvedValue([
		{ id: 1, applicantName: "Asha Nair", email: "asha@example.com", specialization: null, yearsExperience: null, status: "PENDING", appliedAt: "2026-01-01", decidedBy: null, decidedAt: null },
	]),
	approveJobApplication: vi.fn(),
	rejectJobApplication: vi.fn(),
}));
vi.mock("../../api/campuses", () => ({ listCampuses: vi.fn().mockResolvedValue([]) }));
vi.mock("../../api/departments", () => ({ listDepartments: vi.fn().mockResolvedValue([]) }));
vi.mock("../../api/designations", () => ({ listDesignations: vi.fn().mockResolvedValue([]) }));
vi.mock("../../api/roles", () => ({ listRoles: vi.fn().mockResolvedValue([]) }));
vi.mock("../../api/staffProfiles", () => ({
	listStaffProfiles: vi.fn().mockResolvedValue([]),
	createStaffProfile: vi.fn(),
}));

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasAnyPermission: (required: string[]) => required.some((code) => codes.includes(code)),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

beforeEach(() => vi.restoreAllMocks());
afterEach(() => vi.restoreAllMocks());

describe("JobApplicationsPanel RBAC (HR_RECRUITMENT_MANAGE)", () => {
	it("shows Approve and Reject to a recruitment manager", async () => {
		grant("HR_RECRUITMENT_MANAGE");
		render(<JobApplicationsPanel />);
		expect(await screen.findByText("Asha Nair")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Approve" })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Reject" })).toBeInTheDocument();
	});

	it("hides Approve and Reject without the permission", async () => {
		grant("HR_VIEW");
		render(<JobApplicationsPanel />);
		expect(await screen.findByText("Asha Nair")).toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Reject" })).not.toBeInTheDocument();
	});
});

describe("StaffCreatePage RBAC (HR_STAFF_MANAGE)", () => {
	it("enables the submit button for a staff manager", async () => {
		grant("HR_STAFF_MANAGE");
		render(<MemoryRouter><StaffCreatePage /></MemoryRouter>);
		const submit = await screen.findByRole("button", { name: "Add staff member" });
		await waitFor(() => expect(submit).toBeEnabled());
	});

	it("disables the submit button without the permission while a staff profile is requested", async () => {
		grant("MEMBERSHIP_MANAGE");
		render(<MemoryRouter><StaffCreatePage /></MemoryRouter>);
		const submit = await screen.findByRole("button", { name: "Add staff member" });
		expect(submit).toBeDisabled();
	});
});
