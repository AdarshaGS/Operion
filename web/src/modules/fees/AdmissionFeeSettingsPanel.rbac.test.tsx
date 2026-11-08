import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";

vi.mock("../../api/admission", () => ({
	getAdmissionSettings: vi.fn(),
	updateAdmissionSettings: vi.fn(),
}));

import { getAdmissionSettings, updateAdmissionSettings } from "../../api/admission";
import { AdmissionFeeSettingsPanel } from "./AdmissionFeeSettingsPanel";

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasAnyPermission: (required: string[]) => required.some((code) => codes.includes(code)),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

beforeEach(() => {
	vi.clearAllMocks();
	vi.mocked(getAdmissionSettings).mockResolvedValue({ feeClearanceMode: "TRACK" });
});
afterEach(() => vi.restoreAllMocks());

describe("AdmissionFeeSettingsPanel RBAC", () => {
	it("lets an ORGANISATION_MANAGE holder change the mode and save", async () => {
		grant("ORGANISATION_MANAGE");
		vi.mocked(updateAdmissionSettings).mockResolvedValue({ feeClearanceMode: "BLOCK" });
		render(<AdmissionFeeSettingsPanel />);

		const block = await screen.findByRole("radio", { name: "Block" });
		expect(screen.getByRole("radio", { name: "Track" })).toBeChecked();
		expect(block).toBeEnabled();
		fireEvent.click(block);
		fireEvent.click(screen.getByRole("button", { name: "Save" }));

		await waitFor(() => expect(updateAdmissionSettings).toHaveBeenCalledWith({ feeClearanceMode: "BLOCK" }));
		expect(await screen.findByText("Saved.")).toBeInTheDocument();
	});

	it("shows the current mode read-only and hides Save without ORGANISATION_MANAGE", async () => {
		grant("FEE_VIEW");
		render(<AdmissionFeeSettingsPanel />);

		expect(await screen.findByRole("radio", { name: "Track" })).toBeChecked();
		for (const name of ["Off", "Track", "Block"]) {
			expect(screen.getByRole("radio", { name })).toBeDisabled();
		}
		expect(screen.queryByRole("button", { name: "Save" })).not.toBeInTheDocument();
	});

	it("explains each mode, including that Block is not enforced yet and attendance is never blocked", async () => {
		grant("FEE_VIEW");
		render(<AdmissionFeeSettingsPanel />);

		await screen.findByRole("radio", { name: "Off" });
		expect(screen.getByText(/Coming next: will additionally block marks and academic records/)).toBeInTheDocument();
		expect(screen.getByText(/Attendance is never blocked/)).toBeInTheDocument();
	});
});
