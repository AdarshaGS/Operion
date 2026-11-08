import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as AuthContext from "../../auth/AuthContext";

const BOOK = { id: 1, isbn: null, title: "Wings of Fire", author: "A. P. J. Abdul Kalam", publisher: null, category: "Biography", edition: null, status: "ACTIVE" };
const SETTINGS = {
	maxLoansStudent: 2,
	maxLoansStaff: 5,
	borrowingPeriodDaysStudent: 14,
	borrowingPeriodDaysStaff: 30,
	blockIssueOnOverdue: true,
	fineRatePerDay: 1,
};

vi.mock("../../api/books", () => ({
	listBooks: vi.fn(async () => [BOOK]),
	createBook: vi.fn(),
	updateBook: vi.fn(),
}));
vi.mock("../../api/librarySettings", () => ({
	getLibrarySettings: vi.fn(async () => SETTINGS),
	updateLibrarySettings: vi.fn(),
}));

import { BooksPanel } from "./BooksPanel";
import { LibrarySettingsPanel } from "./LibrarySettingsPanel";

function grant(...codes: string[]) {
	vi.spyOn(AuthContext, "useAuth").mockReturnValue({
		hasAnyPermission: (required: string[]) => required.some((code) => codes.includes(code)),
		hasPermission: (code: string) => codes.includes(code),
		session: null,
	} as unknown as ReturnType<typeof AuthContext.useAuth>);
}

afterEach(() => vi.restoreAllMocks());

describe("BooksPanel permissions", () => {
	it("shows add and edit with LIBRARY_CATALOG_MANAGE", async () => {
		grant("LIBRARY_CATALOG_MANAGE");
		render(
			<MemoryRouter>
				<BooksPanel />
			</MemoryRouter>,
		);
		await screen.findByText("Wings of Fire");
		expect(screen.getByRole("button", { name: "Add book" })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Edit book" })).toBeInTheDocument();
	});

	it("hides add and edit without it", async () => {
		grant("LIBRARY_VIEW");
		render(
			<MemoryRouter>
				<BooksPanel />
			</MemoryRouter>,
		);
		await screen.findByText("Wings of Fire");
		expect(screen.queryByRole("button", { name: "Add book" })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Edit book" })).not.toBeInTheDocument();
	});
});

describe("LibrarySettingsPanel permissions", () => {
	it("shows Save with LIBRARY_BORROW_MANAGE", async () => {
		grant("LIBRARY_BORROW_MANAGE");
		render(<LibrarySettingsPanel />);
		expect(await screen.findByRole("button", { name: "Save" })).toBeInTheDocument();
	});

	it("hides Save without it", async () => {
		grant("LIBRARY_VIEW");
		render(<LibrarySettingsPanel />);
		await screen.findByLabelText(/Overdue fine per day/);
		expect(screen.queryByRole("button", { name: "Save" })).not.toBeInTheDocument();
	});
});
