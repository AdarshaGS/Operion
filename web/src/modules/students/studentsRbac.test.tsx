import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";

vi.mock("../../api/applicants", () => ({
	listApplicants: vi.fn(),
	inquireApplicant: vi.fn(),
	rejectApplicant: vi.fn(),
	convertApplicant: vi.fn(),
}));
vi.mock("../../api/persons", async (importOriginal) => ({
	...(await importOriginal<typeof import("../../api/persons")>()),
	listPersons: vi.fn(),
	createPerson: vi.fn(),
}));
vi.mock("../../api/transferRequests", () => ({
	listTransferRequests: vi.fn(),
	approveTransferRequest: vi.fn(),
	rejectTransferRequest: vi.fn(),
}));
vi.mock("../../api/students", async (importOriginal) => ({
	...(await importOriginal<typeof import("../../api/students")>()),
	listStudents: vi.fn(),
	admitStudent: vi.fn(),
}));
vi.mock("../../api/campuses", () => ({ listCampuses: vi.fn() }));
vi.mock("../../api/admission", () => ({ getAdmissionFeePreview: vi.fn() }));
vi.mock("../../api/academicSetupStatus", () => ({ getAcademicSetupStatus: vi.fn() }));
vi.mock("../../api/academicYears", () => ({ listAcademicYears: vi.fn() }));
vi.mock("../../api/schoolClasses", () => ({ listSchoolClasses: vi.fn() }));
vi.mock("../../api/sections", () => ({ listSections: vi.fn() }));
vi.mock("../../api/enrollments", () => ({ createEnrollment: vi.fn() }));
vi.mock("../../api/guardians", () => ({ createOrGetGuardian: vi.fn() }));
vi.mock("../../components/CustomFieldsFormSection", () => ({ CustomFieldsFormSection: () => null }));
vi.mock("../../utils/useSchoolClassLabel", () => ({ useSchoolClassLabel: () => () => "Class" }));

import { listApplicants } from "../../api/applicants";
import { listCampuses } from "../../api/campuses";
import { listPersons } from "../../api/persons";
import { getAcademicSetupStatus } from "../../api/academicSetupStatus";
import { listAcademicYears } from "../../api/academicYears";
import { listSchoolClasses } from "../../api/schoolClasses";
import { listStudents } from "../../api/students";
import { listTransferRequests } from "../../api/transferRequests";
import { ApplicantsPanel } from "./ApplicantsPanel";
import { StudentCreatePage } from "./StudentCreatePage";
import { TransferRequestsInboxPanel } from "./TransferRequestsInboxPanel";

function grant(...codes: string[]) {
	const has = (list: string[]) => list.some((code) => codes.includes(code));
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasPermission: (code: string) => codes.includes(code),
		hasAnyPermission: has,
		isModuleEnabled: () => true,
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

function renderInRouter(node: React.ReactNode) {
	return render(<MemoryRouter>{node}</MemoryRouter>);
}

beforeEach(() => {
	vi.clearAllMocks();
	vi.mocked(listApplicants).mockResolvedValue([
		{ id: 5, personId: 9, inquiryDate: "2026-01-10", source: "Walk-in", notes: null, status: "INQUIRY" },
	]);
	vi.mocked(listPersons).mockResolvedValue([]);
	vi.mocked(listStudents).mockResolvedValue([]);
	vi.mocked(listCampuses).mockResolvedValue([]);
	vi.mocked(listTransferRequests).mockResolvedValue([
		{ id: 3, studentId: 1, fromCampusId: 1, toCampusId: 2, reason: null, status: "PENDING", requestedBy: 1, decidedBy: null, decidedAt: null },
	]);
	vi.mocked(getAcademicSetupStatus).mockResolvedValue({ configured: true });
	vi.mocked(listAcademicYears).mockResolvedValue([]);
	vi.mocked(listSchoolClasses).mockResolvedValue([]);
});

describe("ApplicantsPanel", () => {
	it("shows record-inquiry, convert and reject with STUDENT_MANAGE", async () => {
		grant("STUDENT_MANAGE");
		renderInRouter(<ApplicantsPanel />);

		expect(screen.getByRole("button", { name: /record inquiry/i })).toBeInTheDocument();
		expect(await screen.findByRole("button", { name: /convert to student/i })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /^reject$/i })).toBeInTheDocument();
	});

	it("hides every mutating control (and the actions column) with only STUDENT_VIEW", async () => {
		grant("STUDENT_VIEW");
		renderInRouter(<ApplicantsPanel />);

		expect(await screen.findByText("Walk-in")).toBeInTheDocument();
		expect(screen.queryByRole("button", { name: /record inquiry/i })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: /convert to student/i })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: /^reject$/i })).not.toBeInTheDocument();
	});
});

describe("TransferRequestsInboxPanel", () => {
	it("shows approve and reject with STUDENT_TRANSFER_MANAGE", async () => {
		grant("STUDENT_TRANSFER_MANAGE");
		renderInRouter(<TransferRequestsInboxPanel />);

		expect(await screen.findByRole("button", { name: /^approve$/i })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /^reject$/i })).toBeInTheDocument();
	});

	it("hides approve and reject without STUDENT_TRANSFER_MANAGE but still lists the request", async () => {
		grant("STUDENT_VIEW");
		renderInRouter(<TransferRequestsInboxPanel />);

		await waitFor(() => expect(listTransferRequests).toHaveBeenCalled());
		expect(await screen.findByText("Student #1")).toBeInTheDocument();
		expect(screen.queryByRole("button", { name: /^approve$/i })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: /^reject$/i })).not.toBeInTheDocument();
	});
});

describe("StudentCreatePage submit", () => {
	it("enables Admit student when the caller can manage students and enrolments", async () => {
		grant("STUDENT_MANAGE", "STUDENT_ENROLLMENT_MANAGE", "GUARDIAN_MANAGE");
		renderInRouter(<StudentCreatePage />);

		const submit = await screen.findByRole("button", { name: "Admit student" });
		expect(submit).toBeEnabled();
		expect(screen.getByLabelText("Guardian first name")).toBeInTheDocument();
	});

	it("disables Admit student without STUDENT_ENROLLMENT_MANAGE, since the enrolment step would 403", async () => {
		grant("STUDENT_MANAGE");
		renderInRouter(<StudentCreatePage />);

		const submit = await screen.findByRole("button", { name: "Admit student" });
		expect(submit).toBeDisabled();
	});

	it("drops the guardian section without GUARDIAN_MANAGE", async () => {
		grant("STUDENT_MANAGE", "STUDENT_ENROLLMENT_MANAGE");
		renderInRouter(<StudentCreatePage />);

		await screen.findByRole("button", { name: "Admit student" });
		expect(screen.queryByLabelText("Guardian first name")).not.toBeInTheDocument();
	});
});
