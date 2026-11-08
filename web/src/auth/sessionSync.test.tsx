import { act, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "./AuthContext";
import { ProtectedRoute } from "./ProtectedRoute";
import * as authApi from "../api/auth";
import { api } from "../api/client";
import * as portalApi from "../api/portal";
import { getSession, setSession } from "../api/tokenStore";
import { PlatformAuthProvider } from "../platform/auth/PlatformAuthContext";
import { PlatformProtectedRoute } from "../platform/auth/PlatformProtectedRoute";
import { platformApi } from "../platform/api/platformClient";
import { getPlatformSession, setPlatformSession } from "../platform/api/platformTokenStore";

vi.mock("../api/auth");
vi.mock("../api/portal");

const SESSION = { token: "tok-A", expiresAt: "2099-01-01T00:00:00Z", userId: 1, organisationId: 2 };
const PLATFORM_SESSION = { token: "ptok-A", expiresAt: "2099-01-01T00:00:00Z", platformAdminId: 9, email: "root@operion.test" };

/** Simulates what the browser does in the *other* tabs when one tab writes localStorage. */
function fireStorageEvent(key: string | null) {
	window.dispatchEvent(new StorageEvent("storage", { key }));
}

function Tenant({ onReady }: { onReady?: (call: () => Promise<unknown>) => void }) {
	onReady?.(() => api.get("/api/v1/students"));
	return <div>Dashboard</div>;
}

function renderTenantApp(onReady?: (call: () => Promise<unknown>) => void) {
	return render(
		<MemoryRouter initialEntries={["/dashboard"]}>
			<AuthProvider>
				<Routes>
					<Route path="/login" element={<div>Login page</div>} />
					<Route element={<ProtectedRoute />}>
						<Route path="/dashboard" element={<Tenant onReady={onReady} />} />
					</Route>
				</Routes>
			</AuthProvider>
		</MemoryRouter>,
	);
}

function renderPlatformApp(onReady?: (call: () => Promise<unknown>) => void) {
	function PlatformPage() {
		onReady?.(() => platformApi.get("/api/v1/platform/organisations"));
		return <div>Platform dashboard</div>;
	}
	return render(
		<MemoryRouter initialEntries={["/platform"]}>
			<PlatformAuthProvider>
				<Routes>
					<Route path="/platform/login" element={<div>Platform login page</div>} />
					<Route element={<PlatformProtectedRoute />}>
						<Route path="/platform" element={<PlatformPage />} />
					</Route>
				</Routes>
			</PlatformAuthProvider>
		</MemoryRouter>,
	);
}

beforeEach(() => {
	vi.mocked(authApi.me).mockResolvedValue({
		userId: 1,
		organisationId: 2,
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
	});
	vi.mocked(portalApi.getPortalIdentity).mockResolvedValue({ sessionType: "ADMIN", portalContexts: [] });
});

afterEach(() => {
	vi.unstubAllGlobals();
	localStorage.clear();
});

describe("tenant session: 401 and cross-tab", () => {
	it("an API 401 on a protected page drops the user to /login", async () => {
		setSession(SESSION);
		vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("{}", { status: 401 })));
		let call!: () => Promise<unknown>;
		renderTenantApp((c) => (call = c));
		expect(await screen.findByText("Dashboard")).toBeInTheDocument();

		await act(async () => {
			await call().catch(() => undefined);
		});

		expect(await screen.findByText("Login page")).toBeInTheDocument();
		expect(getSession()).toBeNull();
	});

	it("logging out in another tab (token removed) logs this tab out", async () => {
		setSession(SESSION);
		renderTenantApp();
		expect(await screen.findByText("Dashboard")).toBeInTheDocument();

		// Another tab logs out: it removes the key, the browser fires `storage` here.
		localStorage.removeItem("operion.session");
		act(() => fireStorageEvent("operion.session"));

		expect(await screen.findByText("Login page")).toBeInTheDocument();
	});

	it("localStorage.clear() in another tab (key === null) logs this tab out", async () => {
		setSession(SESSION);
		renderTenantApp();
		expect(await screen.findByText("Dashboard")).toBeInTheDocument();

		localStorage.clear();
		act(() => fireStorageEvent(null));

		expect(await screen.findByText("Login page")).toBeInTheDocument();
	});

	it("ignores storage events for unrelated keys", async () => {
		setSession(SESSION);
		renderTenantApp();
		expect(await screen.findByText("Dashboard")).toBeInTheDocument();

		act(() => fireStorageEvent("operion.theme"));

		await waitFor(() => expect(screen.getByText("Dashboard")).toBeInTheDocument());
		expect(screen.queryByText("Login page")).not.toBeInTheDocument();
	});
});

describe("platform session: 401 and cross-tab", () => {
	it("an API 401 on a platform page drops the admin to /platform/login", async () => {
		setPlatformSession(PLATFORM_SESSION);
		vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("{}", { status: 401 })));
		let call!: () => Promise<unknown>;
		renderPlatformApp((c) => (call = c));
		expect(await screen.findByText("Platform dashboard")).toBeInTheDocument();

		await act(async () => {
			await call().catch(() => undefined);
		});

		expect(await screen.findByText("Platform login page")).toBeInTheDocument();
		expect(getPlatformSession()).toBeNull();
	});

	it("logging out in another tab logs this tab out", async () => {
		setPlatformSession(PLATFORM_SESSION);
		renderPlatformApp();
		expect(await screen.findByText("Platform dashboard")).toBeInTheDocument();

		localStorage.removeItem("operion.platform.session");
		act(() => fireStorageEvent("operion.platform.session"));

		expect(await screen.findByText("Platform login page")).toBeInTheDocument();
	});
});
