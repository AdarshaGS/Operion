// Framework-agnostic token storage - kept separate from AuthContext so the API client
// (which has no business depending on React) can read the current token without a
// circular import back into the auth layer.

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

export function clearSession(): void {
	localStorage.removeItem(STORAGE_KEY);
}
