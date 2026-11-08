import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";

vi.mock("../../api/transferRequests", () => ({
	listTransferRequests: vi.fn(),
	approveTransferRequest: vi.fn(),
	rejectTransferRequest: vi.fn(),
}));
vi.mock("../../api/students", async (importOriginal) => ({
	...(await importOriginal<typeof import("../../api/students")>()),
	listStudents: vi.fn(),
}));
vi.mock("../../api/persons", async (importOriginal) => ({
	...(await importOriginal<typeof import("../../api/persons")>()),
	listPersons: vi.fn(),
}));
vi.mock("../../api/campuses", () => ({ listCampuses: vi.fn() }));

import { ApiError } from "../../api/client";
import { listCampuses } from "../../api/campuses";
import { listPersons } from "../../api/persons";
import { listStudents } from "../../api/students";
import { approveTransferRequest, listTransferRequests, type TransferRequestResponse } from "../../api/transferRequests";
import { TransferRequestsInboxPanel } from "./TransferRequestsInboxPanel";

const pending = { id: 3, studentId: 1, fromCampusId: 1, toCampusId: 2, reason: null, status: "PENDING" } as TransferRequestResponse;

beforeEach(() => {
	vi.clearAllMocks();
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasPermission: (code: string) => code === "STUDENT_TRANSFER_MANAGE",
		hasAnyPermission: (list: string[]) => list.includes("STUDENT_TRANSFER_MANAGE"),
		isModuleEnabled: () => true,
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
	vi.mocked(listStudents).mockResolvedValue([]);
	vi.mocked(listPersons).mockResolvedValue([]);
	vi.mocked(listCampuses).mockResolvedValue([]);
	vi.mocked(listTransferRequests).mockResolvedValue([pending]);
});

function renderPanel() {
	return render(
		<MemoryRouter>
			<TransferRequestsInboxPanel />
		</MemoryRouter>,
	);
}

describe("TransferRequestsInboxPanel", () => {
	it("disappears once the last pending row is approved", async () => {
		vi.mocked(approveTransferRequest).mockResolvedValue(undefined as never);
		renderPanel();
		await screen.findByText("Pending campus transfers");

		vi.mocked(listTransferRequests).mockResolvedValue([]);
		fireEvent.click(screen.getByRole("button", { name: "Approve" }));

		await waitFor(() => expect(screen.queryByText("Pending campus transfers")).not.toBeInTheDocument());
	});

	it("clears a failed-approve error once a retry succeeds, so the emptied panel hides", async () => {
		vi.mocked(approveTransferRequest)
			.mockRejectedValueOnce(new ApiError(409, "Student has unpaid fees"))
			.mockResolvedValueOnce(undefined as never);
		renderPanel();
		await screen.findByText("Pending campus transfers");

		fireEvent.click(screen.getByRole("button", { name: "Approve" }));
		expect(await screen.findByText("Student has unpaid fees")).toBeInTheDocument();

		vi.mocked(listTransferRequests).mockResolvedValue([]);
		fireEvent.click(screen.getByRole("button", { name: "Approve" }));

		await waitFor(() => expect(screen.queryByText("Pending campus transfers")).not.toBeInTheDocument());
		expect(screen.queryByText("Student has unpaid fees")).not.toBeInTheDocument();
	});
});
