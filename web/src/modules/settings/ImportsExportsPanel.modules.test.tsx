import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";
import { ImportsExportsPanel } from "./ImportsExportsPanel";

function withModules(disabled: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasAnyPermission: () => true,
		isModuleEnabled: (code: string) => !disabled.includes(code),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

describe("Imports & exports module gating", () => {
	afterEach(() => vi.restoreAllMocks());

	it("offers every entity when all modules are on", () => {
		withModules([]);
		render(<ImportsExportsPanel />);
		expect(screen.getByText("Library")).toBeInTheDocument();
		expect(screen.getByText("Operations")).toBeInTheDocument();
	});

	it("hides the import cards and their group of a disabled module", () => {
		withModules(["LIBRARY", "TRANSPORT", "INVENTORY"]);
		render(<ImportsExportsPanel />);
		expect(screen.queryByText("Library")).not.toBeInTheDocument();
		expect(screen.queryByText("Operations")).not.toBeInTheDocument();
		expect(screen.getByText("Academics")).toBeInTheDocument();
	});

	it("hides the export buttons of a disabled module", () => {
		withModules(["FEES"]);
		render(<ImportsExportsPanel />);
		fireEvent.click(screen.getByRole("tab", { name: /export data/i }));
		expect(screen.queryByRole("button", { name: /fee structure/i })).not.toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Subjects" })).toBeInTheDocument();
	});
});
