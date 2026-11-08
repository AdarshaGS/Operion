import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";

vi.mock("../../api/admission", () => ({ getAdmissionFeePreview: vi.fn() }));
vi.mock("../../api/campuses", () => ({ listCampuses: vi.fn(async () => []) }));
vi.mock("../../api/academicSetupStatus", () => ({ getAcademicSetupStatus: vi.fn(async () => ({ configured: true })) }));
vi.mock("../../api/academicYears", () => ({ listAcademicYears: vi.fn(async () => [{ id: 3, name: "2026-27" }]) }));
vi.mock("../../api/schoolClasses", () => ({ listSchoolClasses: vi.fn(async () => [{ id: 4, academicYearId: 3, campusId: 1 }]) }));
vi.mock("../../api/sections", () => ({ listSections: vi.fn(async () => [{ id: 11, schoolClassId: 4, name: "A" }]) }));
vi.mock("../../api/enrollments", () => ({ createEnrollment: vi.fn() }));
vi.mock("../../api/guardians", () => ({ createOrGetGuardian: vi.fn() }));
vi.mock("../../components/CustomFieldsFormSection", () => ({ CustomFieldsFormSection: () => null }));
vi.mock("../../utils/useSchoolClassLabel", () => ({ useSchoolClassLabel: () => () => "Class 1" }));

import { getAdmissionFeePreview } from "../../api/admission";
import { StudentCreatePage } from "./StudentCreatePage";

function grantAll() {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasPermission: () => true,
		hasAnyPermission: () => true,
		isModuleEnabled: () => true,
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

async function pick(label: string, option: string) {
	fireEvent.mouseDown(await screen.findByRole("combobox", { name: new RegExp(`^${label}`) }));
	fireEvent.click(within(await screen.findByRole("listbox")).getByText(option));
}

async function chooseYearClassSection() {
	await pick("Academic year", "2026-27");
	await pick("Class", "Class 1");
	await pick("Section", "A");
}

beforeEach(() => {
	vi.clearAllMocks();
	grantAll();
});
afterEach(() => vi.restoreAllMocks());

describe("StudentCreatePage admission fee preview", () => {
	it("shows the fees due once year and section are chosen", async () => {
		vi.mocked(getAdmissionFeePreview).mockResolvedValue({
			mode: "TRACK",
			structures: [{ feeStructureId: 1, categoryName: "Tuition", amount: 48000, firstInstallment: { number: 1, dueDate: "2026-06-15", amount: 12000 } }],
			admissionTotal: 12000,
		});
		render(
			<MemoryRouter>
				<StudentCreatePage />
			</MemoryRouter>,
		);
		await chooseYearClassSection();

		expect(await screen.findByText("Fees due at admission")).toBeInTheDocument();
		expect(getAdmissionFeePreview).toHaveBeenCalledWith(3, 11);
		expect(screen.getByText("Total: ₹12,000")).toBeInTheDocument();
	});

	it("warns when the class has no collect-at-admission fee, and leaves Admit student enabled", async () => {
		vi.mocked(getAdmissionFeePreview).mockResolvedValue({ mode: "TRACK", structures: [], admissionTotal: 0 });
		render(
			<MemoryRouter>
				<StudentCreatePage />
			</MemoryRouter>,
		);
		await chooseYearClassSection();

		expect(await screen.findByText(/No fee is marked 'collect at admission' for this class/)).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Admit student" })).toBeEnabled();
	});

	it("shows nothing and still allows saving when the preview call fails", async () => {
		vi.mocked(getAdmissionFeePreview).mockRejectedValue(new Error("boom"));
		render(
			<MemoryRouter>
				<StudentCreatePage />
			</MemoryRouter>,
		);
		await chooseYearClassSection();

		await waitFor(() => expect(getAdmissionFeePreview).toHaveBeenCalled());
		expect(screen.queryByText("Fees due at admission")).not.toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Admit student" })).toBeEnabled();
	});
});
