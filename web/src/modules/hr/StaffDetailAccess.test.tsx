import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";

const PROFILE = {
	id: 3,
	personId: 7,
	address: null,
	campusId: null,
	employeeCode: "EMP-1",
	designationId: 1,
	designationName: "Teacher",
	departmentId: null,
	departmentName: null,
	reportingManagerId: null,
	dateOfJoining: "2020-06-01",
	employmentType: "PERMANENT",
	status: "ACTIVE",
	invite: null,
};

vi.mock("../../api/staffProfiles", () => ({
	getStaffProfile: vi.fn(),
	listStaffProfiles: vi.fn().mockResolvedValue([]),
	listStaffDocuments: vi.fn().mockResolvedValue([]),
	listStaffAssignments: vi.fn().mockResolvedValue([]),
	listStaffExits: vi.fn().mockResolvedValue([]),
	getStaffBankDetails: vi.fn().mockResolvedValue(null),
	addStaffDocument: vi.fn(),
	changeStaffStatus: vi.fn(),
	giveStaffAccess: vi.fn(),
	recordStaffExit: vi.fn(),
	transferStaff: vi.fn(),
	upsertStaffBankDetails: vi.fn(),
	verifyStaffDocument: vi.fn(),
}));
vi.mock("../../api/persons", () => ({
	getPerson: vi.fn().mockResolvedValue({ id: 7, firstName: "Ravi", lastName: "Menon", email: null, phone: null, status: "ACTIVE" }),
}));
vi.mock("../../api/academicYears", () => ({ listAcademicYears: vi.fn().mockResolvedValue([]) }));
vi.mock("../../api/campuses", () => ({ listCampuses: vi.fn().mockResolvedValue([]) }));
vi.mock("../../api/departments", () => ({ listDepartments: vi.fn().mockResolvedValue([]) }));
vi.mock("../../api/designations", () => ({ listDesignations: vi.fn().mockResolvedValue([]) }));
vi.mock("../../api/leaveBalances", () => ({ allocateLeaveBalance: vi.fn(), getLeaveBalance: vi.fn() }));
vi.mock("../../api/leaveRequests", () => ({
	approveLeaveRequest: vi.fn(),
	cancelLeaveRequest: vi.fn(),
	listLeaveRequests: vi.fn().mockResolvedValue([]),
	raiseLeaveRequest: vi.fn(),
	rejectLeaveRequest: vi.fn(),
}));
vi.mock("../../api/leaveTypes", () => ({ listLeaveTypes: vi.fn().mockResolvedValue([]) }));
vi.mock("../../api/memberships", () => ({ grantMembership: vi.fn(), listMemberships: vi.fn().mockResolvedValue([]) }));
vi.mock("../../api/roles", () => ({ listRoles: vi.fn().mockResolvedValue([]) }));
vi.mock("../../api/users", () => ({ createUser: vi.fn() }));
// Embedded panels fetch their own data and have nothing to do with the access card.
vi.mock("../academics/TeacherTimetablePanel", () => ({ TeacherTimetablePanel: () => null }));
vi.mock("../academics/TeacherUnavailabilityPanel", () => ({ TeacherUnavailabilityPanel: () => null }));
vi.mock("../payroll/SalaryStructurePanel", () => ({ SalaryStructurePanel: () => null }));
vi.mock("../library/BorrowerLoanHistoryPanel", () => ({ BorrowerLoanHistoryPanel: () => null }));
vi.mock("./LeaveCoverPanel", () => ({ LeaveCoverPanel: () => null }));
vi.mock("../../components/LeaveDaysHint", () => ({ LeaveDaysHint: () => null }));
vi.mock("../../components/StaffInviteDialog", () => ({ StaffInviteDialog: () => null }));

import { getStaffProfile } from "../../api/staffProfiles";
import { StaffDetailPage } from "./StaffDetailPage";

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasAnyPermission: (required: string[]) => required.some((code) => codes.includes(code)),
		hasPermission: (code: string) => codes.includes(code),
		isModuleEnabled: () => true,
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

function renderPage() {
	return render(
		<MemoryRouter initialEntries={["/hr/staff/3"]}>
			<Routes>
				<Route path="/hr/staff/:staffProfileId" element={<StaffDetailPage />} />
			</Routes>
		</MemoryRouter>,
	);
}

beforeEach(() => vi.clearAllMocks());
afterEach(() => vi.restoreAllMocks());

describe("StaffDetailPage login access card (StaffProfileResponse.access)", () => {
	it("hides the card, and does not crash, when access is null (caller lacks MEMBERSHIP_VIEW/MANAGE)", async () => {
		grant("HR_VIEW");
		vi.mocked(getStaffProfile).mockResolvedValue({ ...PROFILE, access: null });

		renderPage();

		await waitFor(() => expect(screen.getAllByText(/EMP-1/).length).toBeGreaterThan(0));
		expect(screen.queryByText("Login access")).not.toBeInTheDocument();
		expect(screen.queryByText(/No login - this person cannot sign in/)).not.toBeInTheDocument();
	});

	it("shows the card when the access summary is present", async () => {
		grant("HR_VIEW", "MEMBERSHIP_VIEW");
		vi.mocked(getStaffProfile).mockResolvedValue({
			...PROFILE,
			access: { hasAccess: true, membershipId: 9, roleName: "Teacher role", status: "ACTIVE" },
		});

		renderPage();

		expect(await screen.findByText("Login access")).toBeInTheDocument();
		expect(screen.getByText("Signs in as Teacher role")).toBeInTheDocument();
	});
});
