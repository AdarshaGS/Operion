import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";

vi.mock("../../api/students", async (importOriginal) => ({
	...(await importOriginal<typeof import("../../api/students")>()),
	getStudent: vi.fn(),
	updateStudent: vi.fn(),
}));
vi.mock("../../api/persons", async (importOriginal) => ({
	...(await importOriginal<typeof import("../../api/persons")>()),
	getPerson: vi.fn(),
}));
vi.mock("../../api/enrollments", () => ({ createEnrollment: vi.fn(), listStudentEnrollments: vi.fn() }));
vi.mock("../../api/academicYears", () => ({ listAcademicYears: vi.fn() }));
vi.mock("../../api/schoolClasses", () => ({ listSchoolClasses: vi.fn() }));
vi.mock("../../api/sections", () => ({ listSections: vi.fn() }));
vi.mock("../../components/CustomFieldsFormSection", () => ({
	CustomFieldsFormSection: () => null,
	CustomFieldsDisplay: () => null,
}));
vi.mock("../../utils/useSchoolClassLabel", () => ({ useSchoolClassLabel: () => () => "Grade 5" }));
vi.mock("../../layout/selectedCampus", () => ({
	useCampusFiltered: (items: unknown[]) => items,
	useSelectedCampusId: () => null,
}));
// Heavy panels that are not under test; the three records panels are stubbed so mounting is observable.
vi.mock("./AdmissionFeeCard", () => ({ AdmissionFeeCard: () => null }));
vi.mock("./AdmissionFeePreview", () => ({ AdmissionFeePreview: () => null }));
vi.mock("./StudentAttendanceSummaryPanel", () => ({ StudentAttendanceSummaryPanel: () => null }));
vi.mock("./StudentGuardiansPanel", () => ({ StudentGuardiansPanel: () => <div>guardians-panel</div> }));
vi.mock("../library/BorrowerLoanHistoryPanel", () => ({ BorrowerLoanHistoryPanel: () => null }));
vi.mock("./StudentDocumentsPanel", () => ({ StudentDocumentsPanel: () => <div>documents-panel</div> }));
vi.mock("./StudentTransferPanel", () => ({ StudentTransferPanel: () => <div>transfers-panel</div> }));
vi.mock("../certificates/StudentCertificatesPanel", () => ({ StudentCertificatesPanel: () => <div>certificates-panel</div> }));

import { listAcademicYears } from "../../api/academicYears";
import { listStudentEnrollments, type StudentEnrollmentResponse } from "../../api/enrollments";
import { getPerson } from "../../api/persons";
import { listSchoolClasses } from "../../api/schoolClasses";
import { listSections } from "../../api/sections";
import { getStudent } from "../../api/students";
import { StudentDetailPage } from "./StudentDetailPage";

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasPermission: (code: string) => codes.includes(code),
		hasAnyPermission: (list: string[]) => list.some((code) => codes.includes(code)),
		isModuleEnabled: () => true,
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

function enrollment(overrides: Partial<StudentEnrollmentResponse>): StudentEnrollmentResponse {
	return {
		id: 1,
		studentId: 7,
		academicYearId: 10,
		sectionId: 30,
		schoolClassId: 20,
		rollNumber: 12,
		enrollmentStatus: "ENROLLED",
		current: true,
		enrolledDate: "2025-06-01",
		exitDate: null,
		feeClearanceStatus: "CLEARED",
		...overrides,
	} as StudentEnrollmentResponse;
}

function LocationProbe() {
	return <div data-testid="location">{useLocation().search}</div>;
}

function renderPage(url = "/students/7") {
	return render(
		<MemoryRouter initialEntries={[url]}>
			<LocationProbe />
			<Routes>
				<Route path="/students/:studentId" element={<StudentDetailPage />} />
			</Routes>
		</MemoryRouter>,
	);
}

beforeEach(() => {
	vi.clearAllMocks();
	grant("CERTIFICATE_VIEW");
	vi.mocked(getStudent).mockResolvedValue({ id: 7, personId: 70, status: "ACTIVE", studentId: "S-7" } as Awaited<ReturnType<typeof getStudent>>);
	vi.mocked(getPerson).mockResolvedValue({ id: 70, firstName: "Asha", lastName: "Rao" } as Awaited<ReturnType<typeof getPerson>>);
	vi.mocked(listStudentEnrollments).mockResolvedValue([
		enrollment({}),
		enrollment({ id: 2, current: false, academicYearId: 9, schoolClassId: 19, sectionId: 29, rollNumber: null, enrolledDate: "2024-06-01" }),
	]);
	vi.mocked(listAcademicYears).mockResolvedValue([
		{ id: 10, name: "2025-26" },
		{ id: 9, name: "2024-25" },
	] as Awaited<ReturnType<typeof listAcademicYears>>);
	vi.mocked(listSchoolClasses).mockResolvedValue([]);
	vi.mocked(listSections).mockResolvedValue([]);
});

describe("StudentDetailPage records and enrolment indicator", () => {
	it("shows a tick with the enrolment details instead of the old Enrolled banner", async () => {
		renderPage();
		const tick = await screen.findByRole("img", { name: "Enrolled - roll no. 12, since 2025-06-01" });
		expect(tick).toBeInTheDocument();
		expect(screen.queryByRole("alert")).not.toBeInTheDocument();
		expect(screen.getByText("ACTIVE")).toBeInTheDocument();
	});

	it("shows a not-enrolled marker for an admitted student", async () => {
		vi.mocked(getStudent).mockResolvedValue({ id: 7, personId: 70, status: "ADMITTED" } as Awaited<ReturnType<typeof getStudent>>);
		vi.mocked(listStudentEnrollments).mockResolvedValue([]);
		renderPage();
		expect(await screen.findByRole("img", { name: "Admitted - not enrolled yet" })).toBeInTheDocument();
		expect(screen.queryByRole("img", { name: /^Enrolled/ })).not.toBeInTheDocument();
	});

	it("keeps the records panels out of the page until the Records button opens them lazily", async () => {
		renderPage();
		await screen.findByText("guardians-panel");
		expect(screen.queryByText("documents-panel")).not.toBeInTheDocument();

		fireEvent.click(screen.getByRole("button", { name: "Records" }));
		expect(await screen.findByText("documents-panel")).toBeInTheDocument();
		expect(screen.queryByText("transfers-panel")).not.toBeInTheDocument();

		fireEvent.click(screen.getByRole("tab", { name: "Campus transfers" }));
		expect(await screen.findByText("transfers-panel")).toBeInTheDocument();
		expect(screen.queryByText("documents-panel")).not.toBeInTheDocument();

		fireEvent.click(screen.getByRole("tab", { name: "Certificates" }));
		expect(await screen.findByText("certificates-panel")).toBeInTheDocument();
	});

	it("opens on the tab named by ?records= and lists enrollment history with labels", async () => {
		renderPage("/students/7?records=history");
		const dialog = await screen.findByRole("presentation");
		const table = await within(dialog).findByRole("table");
		await waitFor(() => expect(within(table).getByText("2025-26")).toBeInTheDocument());
		expect(within(table).getByText("2024-25")).toBeInTheDocument();
		expect(within(table).getByText("Current")).toBeInTheDocument();
		expect(within(table).getByText("2024-06-01")).toBeInTheDocument();
	});

	it("hides the Certificates tab without a certificate view permission", async () => {
		grant();
		renderPage();
		fireEvent.click(await screen.findByRole("button", { name: "Records" }));
		expect(await screen.findByRole("tab", { name: "Documents" })).toBeInTheDocument();
		expect(screen.queryByRole("tab", { name: "Certificates" })).not.toBeInTheDocument();
		expect(screen.getByRole("tab", { name: "History" })).toBeInTheDocument();
	});

	it("shows an empty state when the student has no enrollments", async () => {
		vi.mocked(listStudentEnrollments).mockResolvedValue([]);
		renderPage("/students/7?records=history");
		expect(await screen.findByText("No enrollment history yet.")).toBeInTheDocument();
	});

	it("removes the records param on close without mounting the Documents panel while the drawer slides out", async () => {
		renderPage("/students/7?records=history");
		const dialog = await screen.findByRole("dialog", { name: "Records" });
		await within(dialog).findByRole("table");
		expect(screen.getByTestId("location")).toHaveTextContent("records=history");

		fireEvent.click(screen.getByRole("button", { name: "Close records" }));

		expect(screen.getByTestId("location")).toHaveTextContent("");
		expect(screen.getByTestId("location")).not.toHaveTextContent("records");
		expect(screen.queryByText("documents-panel")).not.toBeInTheDocument();
		expect(listAcademicYears).toHaveBeenCalledTimes(1);
	});

	it("does not refetch the enrollment labels when the History tab is revisited", async () => {
		renderPage("/students/7?records=history");
		const dialog = await screen.findByRole("dialog", { name: "Records" });
		await within(dialog).findByText("2025-26");

		fireEvent.click(screen.getByRole("tab", { name: "Documents" }));
		expect(await screen.findByText("documents-panel")).toBeInTheDocument();
		fireEvent.click(screen.getByRole("tab", { name: "History" }));
		await within(screen.getByRole("dialog", { name: "Records" })).findByRole("table");

		expect(listAcademicYears).toHaveBeenCalledTimes(1);
		expect(listSchoolClasses).toHaveBeenCalledTimes(2); // one per distinct academic year, fetched once
	});

	it("falls back to the first tab for an unknown ?records= value", async () => {
		renderPage("/students/7?records=bogus");
		expect(await screen.findByText("documents-panel")).toBeInTheDocument();
		expect(screen.getByRole("tab", { name: "Documents", selected: true })).toBeInTheDocument();
	});

	it("falls back to the first available tab when ?records=certificates is deep-linked without certificate permission", async () => {
		grant();
		renderPage("/students/7?records=certificates");
		expect(await screen.findByText("documents-panel")).toBeInTheDocument();
		expect(screen.queryByText("certificates-panel")).not.toBeInTheDocument();
		expect(screen.queryByRole("tab", { name: "Certificates" })).not.toBeInTheDocument();
		expect(screen.getByRole("tab", { name: "Documents", selected: true })).toBeInTheDocument();
	});

	it("names the drawer and links the tabs to a labelled tabpanel", async () => {
		renderPage("/students/7?records=transfers");
		const dialog = await screen.findByRole("dialog", { name: "Records" });
		const tab = within(dialog).getByRole("tab", { name: "Campus transfers" });
		const panel = within(dialog).getByRole("tabpanel", { name: "Campus transfers" });
		expect(tab.getAttribute("aria-controls")).toBe(panel.id);
	});
});
