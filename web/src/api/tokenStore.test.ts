import { afterEach, describe, expect, it } from "vitest";
import { clearSession, getSession, setSession, type StoredSession } from "./tokenStore";

const SESSION: StoredSession = {
	token: "abc.def.ghi",
	expiresAt: "2026-01-01T00:00:00Z",
	userId: 1,
	organisationId: 2,
};

describe("tokenStore", () => {
	afterEach(() => {
		localStorage.clear();
	});

	it("returns null when nothing has been stored", () => {
		expect(getSession()).toBeNull();
	});

	it("round-trips a session through setSession/getSession", () => {
		setSession(SESSION);
		expect(getSession()).toEqual(SESSION);
	});

	it("clearSession removes the stored session", () => {
		setSession(SESSION);
		clearSession();
		expect(getSession()).toBeNull();
	});

	it("treats corrupted storage as no session rather than throwing", () => {
		localStorage.setItem("operion.session", "{not-valid-json");
		expect(getSession()).toBeNull();
	});
});
