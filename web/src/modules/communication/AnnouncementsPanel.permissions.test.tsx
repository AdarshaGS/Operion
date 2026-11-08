import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";

const DRAFT = {
	id: 7,
	campusId: null,
	title: "Sports day",
	body: "Bring kit",
	audienceType: "ORG",
	audienceRefId: null,
	status: "DRAFT",
	publishedAt: null,
	scheduledAt: null,
	sendOnNonWorkingDays: false,
};

vi.mock("../../api/announcements", () => ({
	listAnnouncements: vi.fn(async (status: string) => (status === "DRAFT" ? [DRAFT] : [])),
	createAnnouncement: vi.fn(),
	publishAnnouncement: vi.fn(),
	cancelAnnouncement: vi.fn(),
	previewAudience: vi.fn(),
}));
vi.mock("../../api/calendar", () => ({ getCalendarDays: vi.fn(async () => ({ nonWorkingDays: [] })) }));
vi.mock("../../api/campuses", () => ({ listCampuses: vi.fn(async () => []) }));
vi.mock("../../api/schoolClasses", () => ({ listSchoolClasses: vi.fn(async () => []) }));
vi.mock("../../api/sections", () => ({ listSections: vi.fn(async () => []) }));
vi.mock("../../api/staffProfiles", () => ({ listStaffProfiles: vi.fn(async () => []) }));
vi.mock("../../api/students", () => ({ listStudents: vi.fn(async () => []) }));
vi.mock("../../api/persons", () => ({ listPersons: vi.fn(async () => []) }));
vi.mock("../../utils/useSchoolClassLabel", () => ({ useSchoolClassLabel: () => () => "Class" }));

import { AnnouncementsPanel } from "./AnnouncementsPanel";

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasAnyPermission: (required: string[]) => required.some((code) => codes.includes(code)),
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

afterEach(() => vi.restoreAllMocks());

describe("AnnouncementsPanel permissions", () => {
	it("shows compose, publish and cancel to a caller holding all three announcement permissions", async () => {
		grant("ANNOUNCEMENT_CREATE", "ANNOUNCEMENT_PUBLISH", "ANNOUNCEMENT_CANCEL");
		render(<AnnouncementsPanel />);

		expect(await screen.findByText("Sports day")).toBeInTheDocument();
		expect(screen.getByText("Compose announcement")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Save as draft" })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Publish" })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Cancel" })).toBeInTheDocument();
	});

	it("hides compose, publish and cancel from a read-only caller but still lists the drafts", async () => {
		grant("COMMUNICATION_VIEW");
		render(<AnnouncementsPanel />);

		expect(await screen.findByText("Sports day")).toBeInTheDocument();
		await waitFor(() => expect(screen.queryByText("Compose announcement")).not.toBeInTheDocument());
		expect(screen.queryByRole("button", { name: "Save as draft" })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Publish" })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Cancel" })).not.toBeInTheDocument();
	});

	it("gates publish and cancel independently (publish without cancel)", async () => {
		grant("ANNOUNCEMENT_PUBLISH");
		render(<AnnouncementsPanel />);

		expect(await screen.findByRole("button", { name: "Publish" })).toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Cancel" })).not.toBeInTheDocument();
		expect(screen.queryByText("Compose announcement")).not.toBeInTheDocument();
	});
});
