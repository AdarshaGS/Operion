import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";

vi.mock("../../api/campuses", () => ({ listCampuses: vi.fn(async () => []) }));
vi.mock("../../api/organisationProfile", () => ({ getOrganisationProfile: vi.fn(async () => ({})) }));
vi.mock("../../api/organisationSettings", () => ({ getOrganisationSettings: vi.fn(async () => ({})) }));
vi.mock("../../api/setupSteps", () => ({
	getCompletedSetupSteps: vi.fn(async () => ({ completedSteps: [] })),
	completeSetupStep: vi.fn(async () => ({ completedSteps: [] })),
}));
// The settings panels are covered by their own gating; the wizard's own buttons are what is under test here.
vi.mock("../settings/OrganisationProfilePanel", () => ({ OrganisationProfilePanel: () => <div>profile panel</div> }));
vi.mock("../settings/BusinessSettingsPanel", () => ({ BusinessSettingsPanel: () => <div>business panel</div> }));
vi.mock("../settings/CampusesPanel", () => ({ CampusesPanel: () => <div>campuses panel</div> }));

import { StructureSetupPage } from "./StructureSetupPage";

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasAnyPermission: (required: string[]) => required.some((code) => codes.includes(code)),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

function renderPage() {
	return render(
		<MemoryRouter>
			<StructureSetupPage />
		</MemoryRouter>,
	);
}

afterEach(() => vi.restoreAllMocks());

describe("StructureSetupPage permissions", () => {
	it("enables Save and continue for an ORGANISATION_MANAGE holder", async () => {
		grant("ORGANISATION_MANAGE");
		renderPage();

		expect(await screen.findByRole("button", { name: "Save and continue" })).toBeEnabled();
	});

	it("disables Save and continue without ORGANISATION_MANAGE but keeps Exit to dashboard", async () => {
		grant("COMMUNICATION_VIEW");
		renderPage();

		expect(await screen.findByRole("button", { name: "Save and continue" })).toBeDisabled();
		expect(screen.getByRole("button", { name: "Exit to dashboard" })).toBeEnabled();
	});
});
