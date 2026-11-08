import { afterEach, describe, expect, it, vi } from "vitest";
import { subscribeToStorageKey } from "./storageSync";
import { onSessionChangedElsewhere, setSession } from "./tokenStore";

const SESSION = { token: "t", expiresAt: "2099-01-01T00:00:00Z", userId: 1, organisationId: 2 };

afterEach(() => localStorage.clear());

describe("subscribeToStorageKey", () => {
	it("fires for the watched key and for storage.clear() (key === null), not for other keys", () => {
		const listener = vi.fn();
		const unsubscribe = subscribeToStorageKey("k", listener);

		window.dispatchEvent(new StorageEvent("storage", { key: "other" }));
		expect(listener).not.toHaveBeenCalled();

		window.dispatchEvent(new StorageEvent("storage", { key: "k" }));
		window.dispatchEvent(new StorageEvent("storage", { key: null }));
		expect(listener).toHaveBeenCalledTimes(2);

		unsubscribe();
		window.dispatchEvent(new StorageEvent("storage", { key: "k" }));
		expect(listener).toHaveBeenCalledTimes(2);
	});
});

describe("onSessionChangedElsewhere", () => {
	it("reports null after another tab removed the session, and the new session after a login elsewhere", () => {
		const listener = vi.fn();
		const unsubscribe = onSessionChangedElsewhere(listener);

		localStorage.removeItem("operion.session");
		window.dispatchEvent(new StorageEvent("storage", { key: "operion.session" }));
		expect(listener).toHaveBeenLastCalledWith(null);

		setSession(SESSION);
		window.dispatchEvent(new StorageEvent("storage", { key: "operion.session" }));
		expect(listener).toHaveBeenLastCalledWith(SESSION);

		unsubscribe();
	});
});
