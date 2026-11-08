import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";
import type { CertificateRequestResponse, CertificateRequestStatus } from "../../api/certificates";
import { CertificatesTable } from "./CertificatesTable";

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasPermission: (code: string) => codes.includes(code),
		hasAnyPermission: (list: string[]) => list.some((code) => codes.includes(code)),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

function request(id: number, status: CertificateRequestStatus): CertificateRequestResponse {
	return {
		id,
		studentId: 1,
		studentName: "Asha Rao",
		admissionNumber: "A-1",
		certificateTypeName: "Bonafide",
		purpose: null,
		certificateNumber: status === "ISSUED" ? "C-1" : null,
		requestedAt: "2026-02-01T00:00:00Z",
		requestedVia: "STAFF",
		status,
		rejectionReason: null,
		cancelReason: null,
		clearanceOverrideReason: null,
		printCount: 0,
		requiresClearance: false,
		manualFields: [],
		manualValues: {},
	} as unknown as CertificateRequestResponse;
}

function renderTable(status: CertificateRequestStatus) {
	render(
		<MemoryRouter>
			<CertificatesTable requests={[request(1, status)]} showStudent onChanged={() => {}} />
		</MemoryRouter>,
	);
}

afterEach(() => vi.restoreAllMocks());

describe("CertificatesTable action gating", () => {
	it("shows Approve and Reject on a pending request to a CERTIFICATE_APPROVE holder", () => {
		grant("CERTIFICATE_APPROVE");
		renderTable("PENDING_APPROVAL");

		expect(screen.getByRole("button", { name: "Approve" })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Reject" })).toBeInTheDocument();
	});

	it("hides Approve and Reject from a caller without CERTIFICATE_APPROVE", () => {
		grant("CERTIFICATE_VIEW", "CERTIFICATE_ISSUE");
		renderTable("PENDING_APPROVAL");

		expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Reject" })).not.toBeInTheDocument();
	});

	it("gates Issue on CERTIFICATE_ISSUE", () => {
		grant("CERTIFICATE_ISSUE");
		renderTable("APPROVED");
		expect(screen.getByRole("button", { name: "Issue" })).toBeInTheDocument();
	});

	it("hides Issue without CERTIFICATE_ISSUE", () => {
		grant("CERTIFICATE_APPROVE");
		renderTable("APPROVED");
		expect(screen.queryByRole("button", { name: "Issue" })).not.toBeInTheDocument();
	});

	it("gates Cancel on CERTIFICATE_CANCEL, independent of CERTIFICATE_ISSUE", () => {
		grant("CERTIFICATE_ISSUE");
		renderTable("ISSUED");

		expect(screen.getByRole("button", { name: "Print" })).toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Cancel" })).not.toBeInTheDocument();
	});

	it("shows Cancel on an issued certificate to a CERTIFICATE_CANCEL holder", () => {
		grant("CERTIFICATE_CANCEL");
		renderTable("ISSUED");

		expect(screen.getByRole("button", { name: "Cancel" })).toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Print" })).not.toBeInTheDocument();
	});
});
