import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";

vi.mock("../../api/gradingScales", () => ({
	listGradingScales: vi.fn(async () => [{ id: 1, name: "CBSE", defaultScale: true, bands: [] }]),
	createGradingScale: vi.fn(),
}));
vi.mock("../../api/examinationSettings", () => ({
	getExaminationSettings: vi.fn(async () => ({ rankingEnabled: true, passFailStrategy: "BOTH", minimumAggregatePercentage: 33 })),
	updateExaminationSettings: vi.fn(),
}));

import { ExaminationSettingsPanel } from "./ExaminationSettingsPanel";
import { GradingScalesPanel } from "./GradingScalesPanel";

/** Mocks the caller's resolved permission set - the same shape useAuth exposes. */
function grant(...codes: string[]) {
	const held = new Set(codes);
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasPermission: (code: string) => held.has(code),
		hasAnyPermission: (wanted: string[]) => wanted.some((code) => held.has(code)),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

afterEach(() => vi.restoreAllMocks());

describe("GradingScalesPanel permissions", () => {
	it("shows Add grading scale to a caller holding GRADING_SCALE_MANAGE", async () => {
		grant("GRADING_SCALE_MANAGE");
		render(<GradingScalesPanel />);
		await screen.findByText("CBSE");
		expect(screen.getByRole("button", { name: /add grading scale/i })).toBeInTheDocument();
	});

	it("hides Add grading scale from a caller who only holds EXAM_VIEW / EXAM_MANAGE", async () => {
		grant("EXAM_VIEW", "EXAM_MANAGE");
		render(<GradingScalesPanel />);
		await screen.findByText("CBSE");
		expect(screen.queryByRole("button", { name: /add grading scale/i })).not.toBeInTheDocument();
	});
});

describe("ExaminationSettingsPanel permissions", () => {
	it("offers an enabled Save to a caller holding EXAM_MANAGE", async () => {
		grant("EXAM_MANAGE");
		render(<ExaminationSettingsPanel />);
		expect(await screen.findByRole("button", { name: "Save" })).toBeEnabled();
		expect(screen.getByRole("switch", { name: /rank/i })).toBeEnabled();
	});

	it("hides Save and disables the policy inputs without EXAM_MANAGE", async () => {
		grant("EXAM_VIEW");
		render(<ExaminationSettingsPanel />);
		await waitFor(() => expect(screen.getByRole("switch", { name: /rank/i })).toBeChecked());
		expect(screen.queryByRole("button", { name: "Save" })).not.toBeInTheDocument();
		expect(screen.getByRole("switch", { name: /rank/i })).toBeDisabled();
		expect(screen.getByRole("combobox", { name: /pass\/fail strategy/i })).toHaveAttribute("aria-disabled", "true");
	});
});
