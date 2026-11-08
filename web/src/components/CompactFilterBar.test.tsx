import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import TextField from "@mui/material/TextField";
import { CompactFilterBar, type CompactFilterBarProps } from "./CompactFilterBar";

function renderBar(props: Partial<CompactFilterBarProps> = {}) {
	return render(
		<CompactFilterBar searchValue="" onSearchChange={() => {}} searchLabel="Search students" activeFilters={[]} {...props}>
			<TextField label="Status" size="small" />
		</CompactFilterBar>,
	);
}

describe("CompactFilterBar", () => {
	it("shows a labelled search input and a Filters button, with the filter controls hidden", () => {
		renderBar();
		expect(screen.getByRole("textbox", { name: "Search students" })).toBeInTheDocument();
		const button = screen.getByRole("button", { name: "Filters" });
		expect(button).toHaveAttribute("aria-haspopup", "dialog");
		expect(button).toHaveAttribute("aria-expanded", "false");
		expect(screen.queryByLabelText("Status")).not.toBeInTheDocument();
	});

	it("reports typing and clears the search through the x button", () => {
		const onSearchChange = vi.fn();
		renderBar({ searchValue: "ash", onSearchChange });
		fireEvent.change(screen.getByRole("textbox", { name: "Search students" }), { target: { value: "ashu" } });
		expect(onSearchChange).toHaveBeenCalledWith("ashu");
		fireEvent.click(screen.getByRole("button", { name: "Clear search" }));
		expect(onSearchChange).toHaveBeenLastCalledWith("");
	});

	it("hides the clear-search button while the search is empty", () => {
		renderBar();
		expect(screen.queryByRole("button", { name: "Clear search" })).not.toBeInTheDocument();
	});

	it("omits the search input when no searchValue is given", () => {
		renderBar({ searchValue: undefined, onSearchChange: undefined });
		expect(screen.queryByRole("textbox", { name: "Search students" })).not.toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Filters" })).toBeInTheDocument();
	});

	it("badges the Filters button with the number of active filters", () => {
		renderBar({
			activeFilters: [
				{ key: "status", label: "Status: Active", onDelete: () => {} },
				{ key: "class", label: "Class: 5", onDelete: () => {} },
			],
		});
		const button = screen.getByRole("button", { name: "Filters, 2 active" });
		expect(button.parentElement).toHaveTextContent("2");
	});

	it("opens the popover with the children, and closes it on Escape", async () => {
		renderBar();
		fireEvent.click(screen.getByRole("button", { name: "Filters" }));
		const dialog = await screen.findByRole("dialog", { name: "Filters" });
		expect(within(dialog).getByLabelText("Status")).toBeInTheDocument();
		// The page behind an open popover is aria-hidden, hence hidden: true.
		expect(screen.getByRole("button", { name: "Filters", hidden: true })).toHaveAttribute("aria-expanded", "true");

		fireEvent.keyDown(dialog, { key: "Escape" });
		await waitFor(() => expect(screen.queryByRole("dialog", { name: "Filters" })).not.toBeInTheDocument());
	});

	it("shows a removable chip per active filter", () => {
		const onDelete = vi.fn();
		renderBar({ activeFilters: [{ key: "status", label: "Status: Active", onDelete }] });
		expect(screen.getByText("Status: Active")).toBeInTheDocument();
		fireEvent.click(screen.getByRole("img", { name: "Remove filter Status: Active" }));
		expect(onDelete).toHaveBeenCalledTimes(1);
	});

	it("shows Clear all only when something is active, and calls the handler", () => {
		const onClearAll = vi.fn();
		const { rerender } = renderBar({ onClearAll });
		expect(screen.queryByRole("button", { name: "Clear all" })).not.toBeInTheDocument();

		rerender(
			<CompactFilterBar searchValue="x" onSearchChange={() => {}} activeFilters={[]} onClearAll={onClearAll}>
				<TextField label="Status" size="small" />
			</CompactFilterBar>,
		);
		fireEvent.click(screen.getByRole("button", { name: "Clear all" }));
		expect(onClearAll).toHaveBeenCalledTimes(1);

		rerender(
			<CompactFilterBar searchValue="" onSearchChange={() => {}} activeFilters={[{ key: "s", label: "Status: Active", onDelete: () => {} }]} onClearAll={onClearAll}>
				<TextField label="Status" size="small" />
			</CompactFilterBar>,
		);
		expect(screen.getByRole("button", { name: "Clear all" })).toBeInTheDocument();
	});

	it("renders the start and end slots", () => {
		renderBar({ startSlot: <span>start-slot</span>, endSlot: <span>12 students</span> });
		expect(screen.getByText("start-slot")).toBeInTheDocument();
		expect(screen.getByText("12 students")).toBeInTheDocument();
	});
});
