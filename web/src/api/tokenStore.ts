// Framework-agnostic token storage - kept separate from AuthContext so the API client
// (which has no business depending on React) can read the current token without a
// circular import back into the auth layer.

import { subscribeToStorageKey } from "./storageSync";

export interface StoredSession {
	token: string;
	expiresAt: string;
	userId: number;
	organisationId: number;
}

const STORAGE_KEY = "operion.session";

export function getSession(): StoredSession | null {
	const raw = localStorage.getItem(STORAGE_KEY);
	if (!raw) return null;
	try {
		return JSON.parse(raw) as StoredSession;
	} catch {
		return null;
	}
}

export function setSession(session: StoredSession): void {
	localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

const clearListeners = new Set<() => void>();

/** Called whenever the stored session is cleared - including by the API client on a rejected token,
 * which has no access to React state - so the auth layer can drop the user back to sign-in. */
export function onSessionCleared(listener: () => void): () => void {
	clearListeners.add(listener);
	return () => clearListeners.delete(listener);
}

export function clearSession(): void {
	localStorage.removeItem(STORAGE_KEY);
	clearListeners.forEach((listener) => listener());
}

/** Called when *another tab* changes or removes the stored session (the `storage` event), with
 * what is stored now - null after a logout elsewhere, a different session after a login
 * elsewhere. Lets this tab drop to sign-in (or adopt the new identity) instead of carrying on
 * with state that no longer matches the token the API client will read from storage. */
export function onSessionChangedElsewhere(listener: (session: StoredSession | null) => void): () => void {
	return subscribeToStorageKey(STORAGE_KEY, () => listener(getSession()));
}
