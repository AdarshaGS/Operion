import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../../api/feeCategories", () => ({
	listFeeCategories: vi.fn(),
	createFeeCategory: vi.fn(),
	updateFeeCategoryAdmission: vi.fn(),
}));

import { createFeeCategory, listFeeCategories, updateFeeCategoryAdmission, type FeeCategoryResponse } from "../../api/feeCategories";
import { FeeCategoriesPanel } from "./FeeCategoriesPanel";

const TUITION: FeeCategoryResponse = {
	id: 5,
	code: "TUITION",
	name: "Tuition Fee",
	description: null,
	status: "ACTIVE",
	categoryType: "GENERAL",
	requiredAtAdmission: false,
};

const HELPER = "Students are invoiced this fee's first instalment when enrolled, and must pay it to clear admission.";

beforeEach(() => {
	vi.clearAllMocks();
	vi.mocked(listFeeCategories).mockResolvedValue([TUITION]);
});
afterEach(() => vi.restoreAllMocks());

describe("FeeCategoriesPanel collect-at-admission", () => {
	it("sends requiredAtAdmission when a category is created with the box ticked", async () => {
		vi.mocked(createFeeCategory).mockResolvedValue({ ...TUITION, id: 6, requiredAtAdmission: true });
		render(<FeeCategoriesPanel />);
		await screen.findByText("Tuition Fee");

		fireEvent.click(screen.getByRole("button", { name: "Add category" }));
		expect(await screen.findByText(HELPER)).toBeInTheDocument();
		fireEvent.change(screen.getByLabelText(/Code/), { target: { value: "ADM" } });
		fireEvent.change(screen.getByLabelText(/^Name/), { target: { value: "Admission Fee" } });
		const box = screen.getByRole("checkbox", { name: "Collect at admission" });
		expect(box).not.toBeChecked();
		fireEvent.click(box);
		fireEvent.click(screen.getByRole("button", { name: "Add" }));

		await waitFor(() =>
			expect(createFeeCategory).toHaveBeenCalledWith({
				code: "ADM",
				name: "Admission Fee",
				description: null,
				categoryType: "GENERAL",
				requiredAtAdmission: true,
			}),
		);
	});

	it("creates with requiredAtAdmission false by default", async () => {
		vi.mocked(createFeeCategory).mockResolvedValue(TUITION);
		render(<FeeCategoriesPanel />);
		await screen.findByText("Tuition Fee");

		fireEvent.click(screen.getByRole("button", { name: "Add category" }));
		fireEvent.change(await screen.findByLabelText(/Code/), { target: { value: "LIB" } });
		fireEvent.change(screen.getByLabelText(/^Name/), { target: { value: "Library" } });
		fireEvent.click(screen.getByRole("button", { name: "Add" }));

		await waitFor(() => expect(createFeeCategory).toHaveBeenCalledWith(expect.objectContaining({ code: "LIB", requiredAtAdmission: false })));
	});

	it("flips an existing category through the admission endpoint and reloads", async () => {
		vi.mocked(updateFeeCategoryAdmission).mockResolvedValue({ ...TUITION, requiredAtAdmission: true });
		render(<FeeCategoriesPanel />);

		const box = await screen.findByRole("checkbox", { name: "Collect Tuition Fee at admission" });
		expect(box).not.toBeChecked();
		vi.mocked(listFeeCategories).mockResolvedValue([{ ...TUITION, requiredAtAdmission: true }]);
		fireEvent.click(box);

		await waitFor(() => expect(updateFeeCategoryAdmission).toHaveBeenCalledWith(5, true));
		await waitFor(() => expect(screen.getByRole("checkbox", { name: "Collect Tuition Fee at admission" })).toBeChecked());
	});
});
