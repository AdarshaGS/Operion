import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";

vi.mock("../../api/privacy", () => ({
	listConsentPurposes: vi.fn(),
	createConsentPurpose: vi.fn(),
	changeConsentPurposeStatus: vi.fn(),
}));

import { listConsentPurposes } from "../../api/privacy";
import { ConsentPurposesTab } from "./ConsentPurposesTab";

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasAnyPermission: (required: string[]) => required.some((code) => codes.includes(code)),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

describe("ConsentPurposesTab RBAC", () => {
	beforeEach(() => {
		vi.mocked(listConsentPurposes).mockResolvedValue([
			{ id: 1, code: "PHOTOS", name: "Publish photos", description: null, status: "ACTIVE" },
		]);
	});
	afterEach(() => vi.restoreAllMocks());

	it("shows Add purpose and Deactivate with PRIVACY_MANAGE", async () => {
		grant("PRIVACY_MANAGE");
		render(<ConsentPurposesTab />);
		expect(await screen.findByText("Publish photos")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /add purpose/i })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Deactivate" })).toBeInTheDocument();
	});

	it("hides Add purpose and Deactivate without PRIVACY_MANAGE", async () => {
		grant("ORGANISATION_MANAGE");
		render(<ConsentPurposesTab />);
		expect(await screen.findByText("Publish photos")).toBeInTheDocument();
		expect(screen.queryByRole("button", { name: /add purpose/i })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Deactivate" })).not.toBeInTheDocument();
	});
});
