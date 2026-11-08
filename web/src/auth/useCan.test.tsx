import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "./AuthContext";
import { useCan } from "./useCan";
import * as authApi from "../api/auth";
import * as portalApi from "../api/portal";
import * as tokenStore from "../api/tokenStore";

vi.mock("../api/auth");
vi.mock("../api/portal");
vi.mock("../api/tokenStore", async (importOriginal) => {
	const actual = await importOriginal<typeof import("../api/tokenStore")>();
	return { ...actual, getSession: vi.fn(), onSessionCleared: vi.fn(() => () => {}) };
});

function meWith(overrides: Partial<authApi.MeResponse>): authApi.MeResponse {
	return {
		userId: 1,
		organisationId: 1,
		organisationName: "Org",
		email: "a@b.c",
		personId: 1,
		personName: "A B",
		firstName: "A",
		lastName: "B",
		campusName: null,
		status: "ACTIVE",
		roleNames: [],
		roles: [],
		permissions: [],
		...overrides,
	};
}

function Probe() {
	const canManage = useCan("STUDENT_MANAGE");
	const canEither = useCan("FEE_MANAGE", "STUDENT_MANAGE");
	return (
		<div>
			<span>manage:{String(canManage)}</span>
			<span>either:{String(canEither)}</span>
		</div>
	);
}

async function renderWith(me: authApi.MeResponse) {
	vi.mocked(tokenStore.getSession).mockReturnValue({ token: "t", expiresAt: "x", userId: 1, organisationId: 1 });
	vi.mocked(authApi.me).mockResolvedValue(me);
	vi.mocked(portalApi.getPortalIdentity).mockResolvedValue({ mode: "ADMIN", contexts: [] } as unknown as portalApi.PortalIdentity);
	render(
		<AuthProvider>
			<Probe />
		</AuthProvider>,
	);
	// Flush the initial /auth/me load: permissions are empty until it resolves.
	await waitFor(() => expect(authApi.me).toHaveBeenCalled());
	await new Promise((resolve) => setTimeout(resolve, 0));
}

describe("useCan permission resolution", () => {
	beforeEach(() => vi.clearAllMocks());
	afterEach(() => vi.restoreAllMocks());

	it("grants a code the role explicitly holds", async () => {
		await renderWith(meWith({ permissions: ["STUDENT_MANAGE"] }));
		await waitFor(() => expect(screen.getByText("manage:true")).toBeInTheDocument());
		expect(screen.getByText("either:true")).toBeInTheDocument();
	});

	it("denies a code the role does not hold", async () => {
		await renderWith(meWith({ permissions: ["FEE_VIEW"] }));
		// Let the /auth/me response apply: "false" is also the pre-load state, so assert only after it settles.
		await new Promise((resolve) => setTimeout(resolve, 20));
		expect(screen.getByText("manage:false")).toBeInTheDocument();
		expect(screen.getByText("either:false")).toBeInTheDocument();
	});

	it("treats the org Owner as holding every code with no explicit grants", async () => {
		await renderWith(meWith({ permissions: [], owner: true }));
		await waitFor(() => expect(screen.getByText("manage:true")).toBeInTheDocument());
		expect(screen.getByText("either:true")).toBeInTheDocument();
	});

	it("treats an ALL_FUNCTIONS grant as holding every code", async () => {
		await renderWith(meWith({ permissions: ["ALL_FUNCTIONS"] }));
		await waitFor(() => expect(screen.getByText("manage:true")).toBeInTheDocument());
	});

	it("does not elevate when the backend omits the owner flag (older backend)", async () => {
		await renderWith(meWith({ permissions: [] }));
		await new Promise((resolve) => setTimeout(resolve, 20));
		expect(screen.getByText("manage:false")).toBeInTheDocument();
	});
});
