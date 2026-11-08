import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";

vi.mock("../../api/academicYears", () => ({ listAcademicYears: vi.fn().mockResolvedValue([]) }));
vi.mock("../../api/persons", () => ({ listPersons: vi.fn().mockResolvedValue([]) }));
vi.mock("../../api/students", () => ({ listStudents: vi.fn().mockResolvedValue([]) }));
vi.mock("../../api/schoolClasses", () => ({ listSchoolClasses: vi.fn().mockResolvedValue([]) }));
vi.mock("../../api/invoices", () => ({
	bulkGenerateInvoices: vi.fn(),
	sendReminders: vi.fn(),
	listOverdueInvoices: vi.fn().mockResolvedValue([
		{
			id: 1,
			invoiceNumber: "INV-1",
			studentEnrollmentId: 1,
			studentId: 1,
			schoolClassId: 1,
			totalAmount: 1000,
			amountPaid: 0,
			outstanding: 1000,
			dueDate: "2026-01-01",
			daysOverdue: 5,
		},
	]),
}));
vi.mock("../../utils/useSchoolClassLabel", () => ({
	useSchoolClassLabel: () => () => "Class",
}));

import { FeeDemandPage } from "./FeeDemandPage";

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasAnyPermission: (required: string[]) => required.some((code) => codes.includes(code)),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

describe("FeeDemandPage permission gating", () => {
	afterEach(() => vi.restoreAllMocks());

	it("shows reminder sending, row selection and bulk invoicing to a caller holding the fee permissions", async () => {
		grant("FEE_REMINDER_SEND", "FEE_INVOICE_MANAGE");
		render(<FeeDemandPage />);

		expect(await screen.findByText("INV-1")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /send reminders/i })).toBeInTheDocument();
		expect(screen.getAllByRole("checkbox").length).toBeGreaterThan(0);
		expect(screen.getByRole("button", { name: /preview/i })).toBeInTheDocument();
	});

	it("keeps the overdue list readable but drops every mutation control without the permissions", async () => {
		grant("FEE_VIEW");
		render(<FeeDemandPage />);

		expect(await screen.findByText("INV-1")).toBeInTheDocument();
		await waitFor(() => expect(screen.queryByRole("button", { name: /send reminders/i })).not.toBeInTheDocument());
		expect(screen.queryAllByRole("checkbox")).toHaveLength(0);
		expect(screen.queryByRole("button", { name: /preview/i })).not.toBeInTheDocument();
	});
});
