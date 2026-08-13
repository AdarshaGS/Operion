// Deliberately separate from ../../api/tokenStore.ts - the platform plane is a
// cryptographically distinct auth system (its own JWT secret, own PlatformAdmin
// identity, see PlatformAuthenticationInterceptor on the backend), so its session is
// kept in its own storage key rather than reusing the org session shape.

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

export function clearPlatformSession(): void {
	localStorage.removeItem(STORAGE_KEY);
}
