import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import { uploadAsset } from "./assets";
import { api, ApiError, apiFetch, downloadFile, isPublicAuthPath } from "./client";
import { postImportFile } from "./imports";
import { downloadRegulatoryExport } from "./regulatoryExports";
import { importStudents } from "./students";
import { downloadSectionTimetableCsv } from "./timetable";
import { getSession, onSessionCleared, setSession } from "./tokenStore";

const SESSION = { token: "tok-123", expiresAt: "2099-01-01T00:00:00Z", userId: 1, organisationId: 2 };

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
	unsubscribe = onSessionCleared(cleared);
});

afterEach(() => {
	unsubscribe();
	vi.unstubAllGlobals();
	localStorage.clear();
});

describe("shared 401 handling (tenant plane)", () => {
	// Every raw-fetch wrapper that used to ignore a 401 - each must now clear the session and throw.
	const wrappers: [string, () => Promise<unknown>][] = [
		["api.get", () => api.get("/api/v1/anything")],
		["api.post", () => api.post("/api/v1/anything", { a: 1 })],
		["downloadFile", () => downloadFile("/api/v1/file", "f.pdf")],
		["importStudents (api/students)", () => importStudents(new File(["x"], "s.csv"))],
		["uploadAsset (api/assets)", () => uploadAsset(new File(["x"], "a.png"))],
		["postImportFile (api/imports)", () => postImportFile("/api/v1/things/import", new File(["x"], "i.csv"), true)],
		["downloadRegulatoryExport (api/regulatoryExports)", () => downloadRegulatoryExport({ id: 1, code: "UDISE" })],
		["downloadSectionTimetableCsv (api/timetable)", () => downloadSectionTimetableCsv(5)],
	];

	it.each(wrappers)("%s: a 401 clears the session, notifies the auth layer and throws a 401 ApiError", async (_name, call) => {
		setSession(SESSION);
		fetchMock.mockResolvedValue(respond(401));

		const error = await call().then(
			() => null,
			(e: unknown) => e,
		);

		expect(error).toBeInstanceOf(ApiError);
		expect((error as ApiError).status).toBe(401);
		expect((error as ApiError).message).toMatch(/session expired/i);
		expect(getSession()).toBeNull();
		expect(cleared).toHaveBeenCalledTimes(1);
	});

	it.each(wrappers)("%s: sends the bearer token", async (_name, call) => {
		setSession(SESSION);
		fetchMock.mockResolvedValue(respond(401));
		await call().catch(() => undefined);
		const headers = new Headers((fetchMock.mock.calls[0][1] as RequestInit).headers);
		expect(headers.get("Authorization")).toBe("Bearer tok-123");
	});

	it("a non-401 error leaves the session alone", async () => {
		setSession(SESSION);
		fetchMock.mockResolvedValue(respond(403, { error: "forbidden" }));
		await expect(api.get("/api/v1/anything")).rejects.toMatchObject({ status: 403, message: "forbidden" });
		expect(getSession()).toEqual(SESSION);
		expect(cleared).not.toHaveBeenCalled();
	});

	it("a 401 with no session attached does not clear or notify anything", async () => {
		fetchMock.mockResolvedValue(respond(401, { error: "unauthenticated" }));
		await expect(api.get("/api/v1/anything")).rejects.toMatchObject({ status: 401, message: "unauthenticated" });
		expect(cleared).not.toHaveBeenCalled();
	});

	it("a network failure still surfaces the friendly unreachable-server error from api.*", async () => {
		fetchMock.mockRejectedValue(new TypeError("Failed to fetch"));
		await expect(api.get("/api/v1/anything")).rejects.toMatchObject({ status: 0 });
	});
});

describe("login endpoints do not trigger the session-expired path (no redirect loop)", () => {
	it.each(["/api/v1/auth/login", "/api/v1/auth/claim-invite", "/api/v1/auth/password-reset/confirm", "/api/v1/auth/verify-email?x=1"])(
		"%s is public",
		(path) => {
			expect(isPublicAuthPath(path)).toBe(true);
		},
	);

	it.each(["/api/v1/auth/me", "/api/v1/auth/logout", "/api/v1/auth/password", "/api/v1/students"])("%s is not public", (path) => {
		expect(isPublicAuthPath(path)).toBe(false);
	});

	it("a wrong-password 401 keeps the backend's message and does not clear a (stale) session", async () => {
		setSession(SESSION);
		fetchMock.mockResolvedValue(respond(401, { error: "Invalid organisation, email or password" }));

		await expect(api.post("/api/v1/auth/login", { email: "a@b.c" })).rejects.toMatchObject({
			status: 401,
			message: "Invalid organisation, email or password",
		});

		expect(getSession()).toEqual(SESSION);
		expect(cleared).not.toHaveBeenCalled();
	});

	it("does not send a stale bearer token to a public endpoint", async () => {
		setSession(SESSION);
		fetchMock.mockResolvedValue(respond(200, {}));
		await apiFetch("/api/v1/auth/login", { method: "POST" });
		const headers = new Headers((fetchMock.mock.calls[0][1] as RequestInit).headers);
		expect(headers.has("Authorization")).toBe(false);
	});
});
