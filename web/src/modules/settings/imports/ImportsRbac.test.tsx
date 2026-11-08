import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../../auth/AuthContext";
import { ExportButton } from "./ExportButton";
import { ImportCard } from "./ImportCard";
import { IMPORT_ENTITIES } from "./importEntities";

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasAnyPermission: (required: string[]) => required.some((code) => codes.includes(code)),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

const subjects = IMPORT_ENTITIES.find((entity) => entity.key === "subjects")!;

describe("Imports & exports RBAC", () => {
	afterEach(() => vi.restoreAllMocks());

	it("uses the controller's permission codes for the subjects entity", () => {
		expect(subjects.importPermissions).toEqual(["SUBJECT_MANAGE"]);
		expect(subjects.exportPermissions).toEqual(["SUBJECT_VIEW"]);
	});

	it("shows Import on a card with the entity's manage code", () => {
		grant("SUBJECT_MANAGE");
		render(<ImportCard config={subjects} />);
		expect(screen.getByRole("button", { name: /^import$/i })).toBeInTheDocument();
	});

	it("hides Import but keeps Download template without the manage code", () => {
		grant("SUBJECT_VIEW");
		render(<ImportCard config={subjects} />);
		expect(screen.queryByRole("button", { name: /^import$/i })).not.toBeInTheDocument();
		expect(screen.getByRole("button", { name: /download template/i })).toBeInTheDocument();
	});

	it("enables the export button with the view code and disables it without", () => {
		grant("SUBJECT_VIEW");
		const { unmount } = render(<ExportButton entity={subjects} onExport={() => undefined} />);
		expect(screen.getByRole("button", { name: "Subjects" })).toBeEnabled();
		unmount();

		grant("SUBJECT_MANAGE");
		render(<ExportButton entity={subjects} onExport={() => undefined} />);
		expect(screen.getByRole("button", { name: "Subjects" })).toBeDisabled();
	});
});
