import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";

vi.mock("../../api/admission", () => ({ getAdmissionFeePreview: vi.fn() }));

import { getAdmissionFeePreview } from "../../api/admission";
import { AdmissionFeePreview } from "./AdmissionFeePreview";

function fees(enabled: boolean) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		isModuleEnabled: () => enabled,
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

const WARNING = "No fee is marked 'collect at admission' for this class - the student will be enrolled with no admission fee.";

beforeEach(() => {
	vi.clearAllMocks();
	fees(true);
});
afterEach(() => vi.restoreAllMocks());

describe("AdmissionFeePreview", () => {
	it("lists the fees due at admission with their total", async () => {
		vi.mocked(getAdmissionFeePreview).mockResolvedValue({
			mode: "TRACK",
			structures: [
				{ feeStructureId: 1, categoryName: "Tuition", amount: 48000, firstInstallment: { number: 1, dueDate: "2026-06-15", amount: 12000 } },
				{ feeStructureId: 2, categoryName: "Admission", amount: 5000, firstInstallment: { number: 1, dueDate: "2026-06-15", amount: 5000 } },
			],
			admissionTotal: 17000,
		});
		render(<AdmissionFeePreview academicYearId="3" sectionId="11" />);

		expect(await screen.findByText("Fees due at admission")).toBeInTheDocument();
		expect(getAdmissionFeePreview).toHaveBeenCalledWith(3, 11);
		expect(screen.getByText(/Tuition - ₹12,000/)).toBeInTheDocument();
		expect(screen.getByText(/Admission - ₹5,000/)).toBeInTheDocument();
		expect(screen.getByText("Total: ₹17,000")).toBeInTheDocument();
	});

	it("warns when no fee is marked collect-at-admission for the class", async () => {
		vi.mocked(getAdmissionFeePreview).mockResolvedValue({ mode: "BLOCK", structures: [], admissionTotal: 0 });
		render(<AdmissionFeePreview academicYearId="3" sectionId="11" />);

		expect(await screen.findByText(WARNING)).toBeInTheDocument();
	});

	it("shows nothing when the mode is OFF", async () => {
		vi.mocked(getAdmissionFeePreview).mockResolvedValue({ mode: "OFF", structures: [], admissionTotal: 0 });
		const { container } = render(<AdmissionFeePreview academicYearId="3" sectionId="11" />);

		await waitFor(() => expect(getAdmissionFeePreview).toHaveBeenCalled());
		expect(screen.queryByText(WARNING)).not.toBeInTheDocument();
		expect(container).toBeEmptyDOMElement();
	});

	it("does not call the preview until both year and section are chosen", () => {
		render(<AdmissionFeePreview academicYearId="3" sectionId="" />);
		expect(getAdmissionFeePreview).not.toHaveBeenCalled();
	});

	it("does not call the preview while the Fees module is off", () => {
		fees(false);
		const { container } = render(<AdmissionFeePreview academicYearId="3" sectionId="11" />);
		expect(getAdmissionFeePreview).not.toHaveBeenCalled();
		expect(container).toBeEmptyDOMElement();
	});

	it("shows nothing when the preview fails, so saving is never blocked", async () => {
		vi.mocked(getAdmissionFeePreview).mockRejectedValue(new Error("boom"));
		const { container } = render(<AdmissionFeePreview academicYearId="3" sectionId="11" />);

		await waitFor(() => expect(getAdmissionFeePreview).toHaveBeenCalled());
		expect(container).toBeEmptyDOMElement();
	});
});
