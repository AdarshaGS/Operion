import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";

vi.mock("../../api/admissions", async (importOriginal) => ({
	...(await importOriginal<typeof import("../../api/admissions")>()),
	getAdmissionStatusCounts: vi.fn(),
	listAdmissionApplicants: vi.fn(),
	getApplicantDocuments: vi.fn(),
	saveApplicantDocuments: vi.fn(),
	startApplicantReview: vi.fn(),
	rejectApplicantWithReason: vi.fn(),
}));
vi.mock("../../api/applicants", () => ({ convertApplicant: vi.fn(), inquireApplicant: vi.fn() }));
vi.mock("../../api/persons", async (importOriginal) => ({
	...(await importOriginal<typeof import("../../api/persons")>()),
	createPerson: vi.fn(),
}));

import { getAdmissionStatusCounts, getApplicantDocuments, listAdmissionApplicants } from "../../api/admissions";
import { AdmissionsPage } from "./AdmissionsPage";

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasPermission: (code: string) => codes.includes(code),
		hasAnyPermission: (list: string[]) => list.some((code) => codes.includes(code)),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

function renderPage() {
	return render(
		<MemoryRouter>
			<AdmissionsPage />
		</MemoryRouter>,
	);
}

beforeEach(() => {
	vi.clearAllMocks();
	vi.mocked(getAdmissionStatusCounts).mockResolvedValue({ ALL: 1, INQUIRY: 1, UNDER_REVIEW: 0, CONVERTED: 0, REJECTED: 0 });
	vi.mocked(listAdmissionApplicants).mockResolvedValue({
		content: [
			{
				id: 7,
				personName: "Meera Nair",
				dateOfBirth: null,
				desiredGrade: null,
				guardianName: "Ravi Nair",
				guardianPhone: null,
				guardianEmail: null,
				status: "INQUIRY",
				inquiryDate: "2026-02-01",
				rejectionReason: null,
			},
		],
		page: 0,
		size: 20,
		totalElements: 1,
		totalPages: 1,
	} as Awaited<ReturnType<typeof listAdmissionApplicants>>);
	vi.mocked(getApplicantDocuments).mockResolvedValue([{ docType: "PHOTO", received: false }]);
});

describe("AdmissionsPage RBAC", () => {
	it("shows Add applicant, Start review, Approve and Reject with STUDENT_MANAGE", async () => {
		grant("STUDENT_VIEW", "STUDENT_MANAGE");
		renderPage();

		expect(await screen.findByText("Meera Nair")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /add applicant/i })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /start review/i })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /^approve$/i })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /^reject$/i })).toBeInTheDocument();
	});

	it("hides the mutating row actions with STUDENT_VIEW only, keeping the read-only Documents button", async () => {
		grant("STUDENT_VIEW");
		renderPage();

		expect(await screen.findByText("Meera Nair")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /^documents$/i })).toBeInTheDocument();
		expect(screen.queryByRole("button", { name: /add applicant/i })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: /start review/i })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: /^approve$/i })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: /^reject$/i })).not.toBeInTheDocument();
	});

	it("opens the documents checklist read-only (no Save, checkboxes disabled) with STUDENT_VIEW only", async () => {
		grant("STUDENT_VIEW");
		renderPage();

		fireEvent.click(await screen.findByRole("button", { name: /^documents$/i }));
		const checkbox = await screen.findByRole("checkbox", { name: /photo/i });
		await waitFor(() => expect(getApplicantDocuments).toHaveBeenCalled());
		// Give the load a chance to finish: a still-loading checkbox is disabled for the wrong reason.
		await new Promise((resolve) => setTimeout(resolve, 50));
		expect(checkbox).toBeDisabled();
		expect(screen.queryByRole("button", { name: /^save$/i })).not.toBeInTheDocument();
	});

	it("lets a STUDENT_MANAGE caller edit and save the documents checklist", async () => {
		grant("STUDENT_VIEW", "STUDENT_MANAGE");
		renderPage();

		fireEvent.click(await screen.findByRole("button", { name: /^documents$/i }));
		const checkbox = await screen.findByRole("checkbox", { name: /photo/i });
		await waitFor(() => expect(checkbox).toBeEnabled());
		expect(screen.getByRole("button", { name: /^save$/i })).toBeInTheDocument();
	});
});
