import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";

vi.mock("../../api/organisationSettings", () => ({
	getOrganisationSettings: vi.fn(),
	updateOrganisationSettings: vi.fn(),
}));
vi.mock("../../api/timezones", () => ({ listTimezones: vi.fn() }));

import { getOrganisationSettings } from "../../api/organisationSettings";
import { listTimezones } from "../../api/timezones";
import { BusinessSettingsPanel } from "./BusinessSettingsPanel";

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasAnyPermission: (required: string[]) => required.some((code) => codes.includes(code)),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

describe("BusinessSettingsPanel RBAC", () => {
	beforeEach(() => {
		vi.mocked(getOrganisationSettings).mockResolvedValue({
			timezone: "Asia/Kolkata",
			defaultCurrency: "INR",
			dateFormat: "dd/MM/yyyy",
			workingDaysMask: 63,
			logoUrl: "",
			primaryColor: "#1976d2",
		} as unknown as Awaited<ReturnType<typeof getOrganisationSettings>>);
		vi.mocked(listTimezones).mockResolvedValue([]);
	});
	afterEach(() => vi.restoreAllMocks());

	it("lets an ORGANISATION_MANAGE holder edit and save", async () => {
		grant("ORGANISATION_MANAGE");
		render(<BusinessSettingsPanel />);
		expect(await screen.findByRole("button", { name: "Save" })).toBeInTheDocument();
		expect(screen.getByLabelText("Logo URL")).toBeEnabled();
	});

	it("shows the values read-only and hides Save without ORGANISATION_MANAGE", async () => {
		grant("STUDENT_VIEW");
		render(<BusinessSettingsPanel />);
		expect(await screen.findByLabelText("Logo URL")).toBeDisabled();
		expect(screen.queryByRole("button", { name: "Save" })).not.toBeInTheDocument();
	});
});
