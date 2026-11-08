import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";
import type { FeeClearanceViewResponse } from "../../api/admission";
import type { StudentEnrollmentResponse } from "../../api/enrollments";

vi.mock("../../api/admission", () => ({ getFeeClearance: vi.fn() }));
vi.mock("../fees/CollectFeeDialog", () => ({
	CollectFeeDialog: ({ open, target }: { open: boolean; target?: { studentEnrollmentId: number } | null }) =>
		open ? <div>Collect dialog for enrollment {target?.studentEnrollmentId}</div> : null,
}));

import { getFeeClearance } from "../../api/admission";
import { AdmissionFeeCard } from "./AdmissionFeeCard";

const ENROLLMENT: StudentEnrollmentResponse = {
	id: 21,
	studentId: 31,
	academicYearId: 1,
	sectionId: 11,
	schoolClassId: 4,
	rollNumber: 1,
	enrollmentStatus: "ACTIVE",
	current: true,
	enrolledDate: "2026-06-01",
	exitDate: null,
	feeClearanceStatus: "PENDING",
};

const PENDING: FeeClearanceViewResponse = {
	status: "PENDING",
	mode: "TRACK",
	steps: [
		{ key: "ENROLLED", done: true, at: "2026-06-01T09:00:00Z" },
		{ key: "FEES_ASSIGNED", done: true, at: "2026-06-01T09:00:01Z" },
		{ key: "INVOICES_CREATED", done: true, at: "2026-06-01T09:00:02Z" },
		{ key: "FIRST_PAYMENT", done: false, at: null },
	],
	invoices: [{ id: 7, number: "INV-0007", total: 12000, paid: 2000, outstanding: 10000, dueDate: "2026-06-15" }],
	override: null,
};

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasAnyPermission: (required: string[]) => required.some((code) => codes.includes(code)),
		hasPermission: (code: string) => codes.includes(code),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

beforeEach(() => vi.clearAllMocks());
afterEach(() => vi.restoreAllMocks());

describe("AdmissionFeeCard", () => {
	it("shows the pending chip, the four steps and the outstanding amount, with Collect for FEE_COLLECT", async () => {
		grant("FEE_VIEW", "FEE_COLLECT");
		vi.mocked(getFeeClearance).mockResolvedValue(PENDING);
		render(<AdmissionFeeCard enrollment={ENROLLMENT} />);

		expect(await screen.findByText("Admission fee pending")).toBeInTheDocument();
		expect(getFeeClearance).toHaveBeenCalledWith(31, 21);
		for (const label of ["Enrolled", "Fees assigned", "Invoices created", "First payment"]) {
			expect(screen.getByText(label)).toBeInTheDocument();
		}
		expect(screen.getByTestId("admission-step-FIRST_PAYMENT")).toHaveTextContent("Not yet");
		expect(screen.getByTestId("admission-step-ENROLLED")).not.toHaveTextContent("Not yet");
		expect(screen.getByText("INV-0007")).toBeInTheDocument();
		expect(screen.getByText("₹10,000")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Collect" })).toBeInTheDocument();
	});

	it("opens the existing collect dialog for this enrollment", async () => {
		grant("FEE_VIEW", "FEE_COLLECT");
		vi.mocked(getFeeClearance).mockResolvedValue(PENDING);
		render(<AdmissionFeeCard enrollment={ENROLLMENT} />);

		(await screen.findByRole("button", { name: "Collect" })).click();
		expect(await screen.findByText("Collect dialog for enrollment 21")).toBeInTheDocument();
	});

	it("hides Collect without FEE_COLLECT", async () => {
		grant("FEE_VIEW");
		vi.mocked(getFeeClearance).mockResolvedValue(PENDING);
		render(<AdmissionFeeCard enrollment={ENROLLMENT} />);

		await screen.findByText("Admission fee pending");
		expect(screen.queryByRole("button", { name: "Collect" })).not.toBeInTheDocument();
	});

	it("shows a cleared student without Collect", async () => {
		grant("FEE_VIEW", "FEE_COLLECT");
		vi.mocked(getFeeClearance).mockResolvedValue({
			...PENDING,
			status: "CLEARED",
			steps: PENDING.steps.map((step) => (step.key === "FIRST_PAYMENT" ? { ...step, done: true, at: "2026-06-10T09:00:00Z" } : step)),
			invoices: [{ id: 7, number: "INV-0007", total: 12000, paid: 12000, outstanding: 0, dueDate: "2026-06-15" }],
		});
		render(<AdmissionFeeCard enrollment={{ ...ENROLLMENT, feeClearanceStatus: "CLEARED" }} />);

		expect(await screen.findByText("Admission fee cleared")).toBeInTheDocument();
		expect(screen.getByTestId("admission-step-FIRST_PAYMENT")).not.toHaveTextContent("Not yet");
		expect(screen.queryByRole("button", { name: "Collect" })).not.toBeInTheDocument();
	});

	it("is hidden when no admission fee is required and the mode is OFF", async () => {
		grant("FEE_VIEW", "FEE_COLLECT");
		vi.mocked(getFeeClearance).mockResolvedValue({ status: "NOT_REQUIRED", mode: "OFF", steps: PENDING.steps, invoices: [], override: null });
		const { container } = render(<AdmissionFeeCard enrollment={{ ...ENROLLMENT, feeClearanceStatus: "NOT_REQUIRED" }} />);

		await waitFor(() => expect(getFeeClearance).toHaveBeenCalled());
		expect(container).toBeEmptyDOMElement();
	});

	it("still shows when no fee is required but tracking is on", async () => {
		grant("FEE_VIEW");
		vi.mocked(getFeeClearance).mockResolvedValue({ status: "NOT_REQUIRED", mode: "TRACK", steps: PENDING.steps, invoices: [], override: null });
		render(<AdmissionFeeCard enrollment={{ ...ENROLLMENT, feeClearanceStatus: "NOT_REQUIRED" }} />);

		expect(await screen.findByText("No admission fee")).toBeInTheDocument();
	});
});
