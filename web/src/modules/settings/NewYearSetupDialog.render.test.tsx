import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AcademicYearResponse } from "../../api/academicYears";
import type { SchoolTemplateApplyResponse, SchoolTemplatePreviewResponse } from "../../api/schoolTemplate";

vi.mock("../../api/schoolTemplate", () => ({
	getSchoolTemplatePreview: vi.fn(),
	applySchoolTemplate: vi.fn(),
}));

import { applySchoolTemplate, getSchoolTemplatePreview } from "../../api/schoolTemplate";
import { NewYearSetupDialog } from "./NewYearSetupDialog";

const YEAR: AcademicYearResponse = { id: 7, name: "2027-28", startDate: "2027-06-01", endDate: "2028-03-31", current: false, status: "DRAFT" };

const status = (name: string, alreadyExists: boolean) => ({ name, alreadyExists });

const PREVIEW = {
	classes: [status("Grade 1", false), status("Grade 2", false), status("Grade 3", true)],
	sections: [status("A", false)],
} as unknown as SchoolTemplatePreviewResponse;

const APPLIED = {
	classesCreated: ["Grade 1", "Grade 2"],
	classesSkipped: ["Grade 3"],
	sectionsCreated: ["Grade 1 - A", "Grade 2 - A"],
	sectionsSkipped: [],
	gradesCreated: [],
	subjectsCreated: [],
	departmentsCreated: [],
	designationsCreated: [],
	rolesCreated: [],
	feeCategoriesCreated: [],
	itemCategoriesCreated: [],
	itemsCreated: [],
	assetTypesCreated: [],
	leaveTypesCreated: [],
	certificateTypesCreated: [],
	classesNote: "There is no active campus, so no classes were created - add a campus, then apply again.",
} as unknown as SchoolTemplateApplyResponse;

function renderDialog(props: Partial<Parameters<typeof NewYearSetupDialog>[0]> = {}) {
	const onClose = vi.fn();
	const onCopyForward = vi.fn();
	render(
		<MemoryRouter initialEntries={["/settings"]}>
			<Routes>
				<Route
					path="/settings"
					element={<NewYearSetupDialog year={YEAR} canCopyForward onClose={onClose} onCopyForward={onCopyForward} {...props} />}
				/>
				<Route path="/academics/setup" element={<div>Academics setup page</div>} />
			</Routes>
		</MemoryRouter>,
	);
	return { onClose, onCopyForward };
}

beforeEach(() => {
	vi.clearAllMocks();
	vi.mocked(getSchoolTemplatePreview).mockResolvedValue(PREVIEW);
	vi.mocked(applySchoolTemplate).mockResolvedValue(APPLIED);
});

describe("NewYearSetupDialog", () => {
	it("previews what the starter template would do in that year", async () => {
		renderDialog();

		expect(screen.getByText("Set up 2027-28")).toBeInTheDocument();
		expect(await screen.findByText("Classes: 2 to create, 1 already exist")).toBeInTheDocument();
		expect(screen.getByText("Sections: 1 to create, 0 already exist")).toBeInTheDocument();
		expect(getSchoolTemplatePreview).toHaveBeenCalledWith(7);
	});

	it("applies the template to that year and shows the summary with the note and a link to Academics setup", async () => {
		renderDialog();
		await screen.findByText("Classes: 2 to create, 1 already exist");

		fireEvent.click(screen.getByRole("button", { name: "Apply template" }));

		expect(await screen.findByText("2027-28 now has 2 new classes and 2 new sections.")).toBeInTheDocument();
		expect(applySchoolTemplate).toHaveBeenCalledWith(undefined, 7);
		expect(screen.getByText(/no active campus/)).toBeInTheDocument();

		fireEvent.click(screen.getByRole("button", { name: "Go to Academics setup" }));
		expect(await screen.findByText("Academics setup page")).toBeInTheDocument();
	});

	it("does not offer Apply until the preview has loaded", async () => {
		renderDialog();

		expect(screen.getByRole("button", { name: "Apply template" })).toBeDisabled();
		await waitFor(() => expect(screen.getByRole("button", { name: "Apply template" })).toBeEnabled());
	});

	it("disables Apply when the year already has everything", async () => {
		vi.mocked(getSchoolTemplatePreview).mockResolvedValue({
			classes: [status("Grade 1", true)],
			sections: [status("A", true)],
		} as unknown as SchoolTemplatePreviewResponse);
		renderDialog();

		expect(await screen.findByText("This year already has every class and section from the template.")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Apply template" })).toBeDisabled();
	});

	it("closes on Skip for now without applying anything", async () => {
		const { onClose } = renderDialog();
		await screen.findByText("Classes: 2 to create, 1 already exist");

		fireEvent.click(screen.getByRole("button", { name: "Skip for now" }));

		expect(onClose).toHaveBeenCalledTimes(1);
		expect(applySchoolTemplate).not.toHaveBeenCalled();
	});

	it("hands over to copy-forward for that year when chosen", async () => {
		const { onCopyForward } = renderDialog();
		await screen.findByText("Classes: 2 to create, 1 already exist");

		fireEvent.click(screen.getByRole("radio", { name: "Copy forward from an earlier year" }));
		fireEvent.click(screen.getByRole("button", { name: "Continue to copy forward" }));

		expect(onCopyForward).toHaveBeenCalledWith(YEAR);
		expect(applySchoolTemplate).not.toHaveBeenCalled();
	});

	it("does not offer copy-forward when there is no other year", async () => {
		renderDialog({ canCopyForward: false });
		await screen.findByText("Classes: 2 to create, 1 already exist");

		expect(screen.getByRole("radio", { name: "Copy forward from an earlier year" })).toBeDisabled();
		expect(screen.getByText("There is no other academic year to copy from yet.")).toBeInTheDocument();
	});

	it("shows the error when applying fails", async () => {
		const { ApiError } = await import("../../api/client");
		vi.mocked(applySchoolTemplate).mockRejectedValue(new ApiError(409, "Academic year 2027-28 is closed"));
		renderDialog();
		await screen.findByText("Classes: 2 to create, 1 already exist");

		fireEvent.click(screen.getByRole("button", { name: "Apply template" }));

		expect(await screen.findByText("Academic year 2027-28 is closed")).toBeInTheDocument();
	});
});
