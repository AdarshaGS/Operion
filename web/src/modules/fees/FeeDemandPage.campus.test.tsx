import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { setSelectedCampusId } from "../../layout/selectedCampus";

const CLASSES = [
	{ id: 1, academicYearId: 1, campusId: 1, campusName: "Main Campus", gradeLevelId: 1, displayName: "LKG Main", status: "ACTIVE" },
	{ id: 2, academicYearId: 1, campusId: 2, campusName: "Branch", gradeLevelId: 1, displayName: "LKG Branch", status: "ACTIVE" },
	{ id: 3, academicYearId: 1, campusId: 2, campusName: "Branch", gradeLevelId: 2, displayName: "UKG Branch", status: "ACTIVE" },
];
const emptyResult = { dryRun: true, toGenerate: 0, generated: 0, alreadyInvoiced: 0, invoiceIds: [], failures: [] };

vi.mock("../../auth/Can", () => ({ Can: ({ children }: { children: React.ReactNode }) => <>{children}</> }));
vi.mock("../../auth/useCan", () => ({ useCan: () => true }));
vi.mock("../../api/schoolClasses", () => ({ listSchoolClasses: vi.fn(async () => CLASSES) }));
vi.mock("../../api/students", () => ({ listStudents: vi.fn(async () => []) }));
vi.mock("../../api/persons", () => ({ listPersons: vi.fn(async () => []) }));
vi.mock("../../api/academicYears", () => ({
	listAcademicYears: vi.fn(async () => [{ id: 1, name: "2026-27", current: true }]),
}));
vi.mock("../../api/invoices", () => ({
	listOverdueInvoices: vi.fn(async () => [
		{ id: 100, invoiceNumber: "INV-100", studentEnrollmentId: 1, studentId: 1, schoolClassId: 1, totalAmount: 10, amountPaid: 0, outstanding: 10, dueDate: "2026-01-01", daysOverdue: 3 },
		{ id: 200, invoiceNumber: "INV-200", studentEnrollmentId: 2, studentId: 2, schoolClassId: 2, totalAmount: 10, amountPaid: 0, outstanding: 10, dueDate: "2026-01-01", daysOverdue: 3 },
	]),
	sendReminders: vi.fn(async () => []),
	bulkGenerateInvoices: vi.fn(async () => emptyResult),
}));
vi.mock("../../utils/useSchoolClassLabel", () => ({
	useSchoolClassLabel: () => (c: { displayName: string | null }) => c.displayName ?? "Class",
}));

import { bulkGenerateInvoices } from "../../api/invoices";
import { FeeDemandPage } from "./FeeDemandPage";

beforeEach(() => {
	vi.mocked(bulkGenerateInvoices).mockClear();
	act(() => setSelectedCampusId(null));
});

/** Options of the nth "Class" picker: 0 is the bulk-invoice panel's, 1 is the overdue list's filter. */
async function options(index: number): Promise<string[]> {
	fireEvent.mouseDown(screen.getAllByRole("combobox", { name: "Class" })[index]);
	const list = within(await screen.findByRole("listbox")).getAllByRole("option").map((o) => o.textContent ?? "");
	fireEvent.keyDown(screen.getByRole("listbox"), { key: "Escape" });
	await waitFor(() => expect(screen.queryByRole("listbox")).toBeNull());
	return list;
}

describe("FeeDemandPage campus scope", () => {
	it("shows every campus's overdue invoices and classes under All campuses", async () => {
		render(<FeeDemandPage />);
		await screen.findByText("INV-100");
		expect(screen.getByText("INV-200")).toBeInTheDocument();
		await waitFor(async () => expect(await options(1)).toEqual(["All classes", "LKG Main", "LKG Branch", "UKG Branch"]));
		expect(await options(0)).toEqual(["All classes", "LKG Main", "LKG Branch", "UKG Branch"]);
	});

	it("lists only the picked campus's overdue invoices and class filter options", async () => {
		act(() => setSelectedCampusId(2));
		render(<FeeDemandPage />);
		await screen.findByText("INV-200");
		expect(screen.queryByText("INV-100")).toBeNull();
		await waitFor(async () => expect(await options(1)).toEqual(["All classes", "LKG Branch", "UKG Branch"]));
		expect(await options(0)).toEqual(["All classes in this campus", "LKG Branch", "UKG Branch"]);
	});

	it("labels an invoice's class from every class, never only the campus's (display is not filtered)", async () => {
		render(<FeeDemandPage />);
		const row = (await screen.findByText("INV-200")).closest("tr") as HTMLElement;
		await waitFor(() => expect(within(row).getByText("LKG Branch")).toBeInTheDocument());
		// switch to the campus that does NOT own the class: the invoice is hidden (a list), but nothing else breaks
		act(() => setSelectedCampusId(1));
		await waitFor(() => expect(screen.queryByText("INV-200")).toBeNull());
		expect(screen.getByText("INV-100")).toBeInTheDocument();
	});

	it("bulk invoice generation with a campus picked runs once per class of that campus, never organisation-wide", async () => {
		act(() => setSelectedCampusId(2));
		render(<FeeDemandPage />);
		await waitFor(async () => expect(await options(1)).toContain("LKG Branch"));
		await waitFor(() => expect(screen.getByRole("button", { name: "Preview" })).toBeEnabled());
		fireEvent.click(screen.getByRole("button", { name: "Preview" }));
		await waitFor(() => expect(bulkGenerateInvoices).toHaveBeenCalledTimes(2));
		const calls = vi.mocked(bulkGenerateInvoices).mock.calls.map(([request]) => request.schoolClassId);
		expect(calls.sort()).toEqual([2, 3]);
	});

	it("bulk invoice generation under All campuses stays one organisation-wide call", async () => {
		render(<FeeDemandPage />);
		await waitFor(() => expect(screen.getByRole("button", { name: "Preview" })).toBeEnabled());
		fireEvent.click(screen.getByRole("button", { name: "Preview" }));
		await waitFor(() => expect(bulkGenerateInvoices).toHaveBeenCalledTimes(1));
		expect(vi.mocked(bulkGenerateInvoices).mock.calls[0][0].schoolClassId).toBeUndefined();
	});

	it("explains a campus with no classes and disables the bulk preview", async () => {
		act(() => setSelectedCampusId(99));
		render(<FeeDemandPage />);
		expect((await screen.findAllByText("No classes in the selected campus.")).length).toBeGreaterThan(0);
		expect(screen.getByRole("button", { name: "Preview" })).toBeDisabled();
	});
});
