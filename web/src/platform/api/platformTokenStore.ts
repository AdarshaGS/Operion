// Deliberately separate from ../../api/tokenStore.ts - the platform plane is a
// cryptographically distinct auth system (its own JWT secret, own PlatformAdmin
// identity, see PlatformAuthenticationInterceptor on the backend), so its session is
// kept in its own storage key rather than reusing the org session shape.

import { subscribeToStorageKey } from "../../api/storageSync";

export interface StoredPlatformSession {
	token: string;
	expiresAt: string;
	platformAdminId: number;
	email: string;
}

const STORAGE_KEY = "operion.platform.session";

export function getPlatformSession(): StoredPlatformSession | null {
	const raw = localStorage.getItem(STORAGE_KEY);
	if (!raw) return null;
	try {
		return JSON.parse(raw) as StoredPlatformSession;
	} catch {
		return null;
	}
}

export function setPlatformSession(session: StoredPlatformSession): void {
	localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

const clearListeners = new Set<() => void>();

/** Called whenever the stored platform session is cleared - including by the platform API client on
 * a rejected token, which has no access to React state - so PlatformAuthContext can drop the admin
 * back to the platform sign-in. Same contract as onSessionCleared in ../../api/tokenStore.ts. */
export function onPlatformSessionCleared(listener: () => void): () => void {
	clearListeners.add(listener);
	return () => clearListeners.delete(listener);
}

export function clearPlatformSession(): void {
	localStorage.removeItem(STORAGE_KEY);
	clearListeners.forEach((listener) => listener());
}

/** Called when *another tab* changes or removes the stored platform session (the `storage` event),
 * with what is stored now. See onSessionChangedElsewhere in ../../api/tokenStore.ts. */
export function onPlatformSessionChangedElsewhere(listener: (session: StoredPlatformSession | null) => void): () => void {
	return subscribeToStorageKey(STORAGE_KEY, () => listener(getPlatformSession()));
}
