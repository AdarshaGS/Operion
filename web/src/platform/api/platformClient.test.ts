import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import { isPublicPlatformPath, PlatformApiError, platformApi } from "./platformClient";
import { getPlatformSession, onPlatformSessionCleared, setPlatformSession } from "./platformTokenStore";

const SESSION = { token: "ptok", expiresAt: "2099-01-01T00:00:00Z", platformAdminId: 9, email: "root@operion.test" };

function respond(status: number, body: unknown = { error: "nope" }): Response {
	return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

let fetchMock: ReturnType<typeof vi.fn>;
let cleared: Mock<() => void>;
let unsubscribe: () => void;

beforeEach(() => {
	fetchMock = vi.fn();
	vi.stubGlobal("fetch", fetchMock);
	cleared = vi.fn<() => void>();
	unsubscribe = onPlatformSessionCleared(cleared);
});

afterEach(() => {
	unsubscribe();
	vi.unstubAllGlobals();
	localStorage.clear();
});

describe("platform client 401 handling", () => {
	it("a 401 clears the platform session, notifies the auth layer and throws", async () => {
		setPlatformSession(SESSION);
		fetchMock.mockResolvedValue(respond(401));

		await expect(platformApi.get("/api/v1/platform/organisations")).rejects.toBeInstanceOf(PlatformApiError);

		expect(getPlatformSession()).toBeNull();
		expect(cleared).toHaveBeenCalledTimes(1);
	});

	it("sends the platform bearer token", async () => {
		setPlatformSession(SESSION);
		fetchMock.mockResolvedValue(respond(200, []));
		await platformApi.get("/api/v1/platform/organisations");
		const headers = new Headers((fetchMock.mock.calls[0][1] as RequestInit).headers);
		expect(headers.get("Authorization")).toBe("Bearer ptok");
	});

	it("a wrong-password 401 on platform login is not a session expiry (no redirect loop)", async () => {
		fetchMock.mockResolvedValue(respond(401, { error: "Invalid credentials" }));
		await expect(platformApi.post("/api/v1/platform/auth/login", { email: "x", password: "y" })).rejects.toMatchObject({
			status: 401,
			message: "Invalid credentials",
		});
		expect(cleared).not.toHaveBeenCalled();
	});

	it("does not clear on non-401 failures", async () => {
		setPlatformSession(SESSION);
		fetchMock.mockResolvedValue(respond(500));
		await expect(platformApi.get("/api/v1/platform/organisations")).rejects.toMatchObject({ status: 500 });
		expect(getPlatformSession()).toEqual(SESSION);
	});

	it("only the login endpoint is public", () => {
		expect(isPublicPlatformPath("/api/v1/platform/auth/login")).toBe(true);
		expect(isPublicPlatformPath("/api/v1/platform/auth/me")).toBe(false);
	});
});
