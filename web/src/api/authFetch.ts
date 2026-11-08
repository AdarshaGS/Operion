// The single fetch path for every authenticated HTTP call, tenant and platform alike. Each
// plane supplies a SessionAuth binding (where its token lives, how to drop it, which error
// to throw) and gets one shared rule for a rejected token: clear the session - which the
// auth contexts observe and answer by routing the user to sign-in - and throw. Nothing in
// src/ should call fetch() against the API directly.

export const SESSION_EXPIRED_MESSAGE = "Session expired - please log in again";

export interface SessionAuth {
	/** The bearer token currently stored for this plane, or null when signed out. */
	getToken: () => string | null;
	/** Drops the stored session and notifies the auth layer (which redirects to login). */
	clearSession: () => void;
	/** The error thrown to the caller once the session has been dropped. */
	expiredError: () => Error;
}

export interface SessionFetchOptions {
	/**
	 * For endpoints that run before a session exists (login, claim-invite, password reset...).
	 * No bearer token is attached and a 401 is left for the caller to read - it means "wrong
	 * credentials", not "session expired", and treating it as the latter would also loop a
	 * rejected login back into another login redirect.
	 */
	public?: boolean;
}

/**
 * fetch() plus the shared auth contract: attaches the bearer token (when one is stored and
 * the call isn't public) and, if the server answers 401 to a request that carried one,
 * clears the session and throws auth.expiredError(). Everything else - including the
 * response body handling and error mapping for non-401 statuses - stays with the caller.
 */
export async function fetchWithSession(
	auth: SessionAuth,
	url: string,
	init: RequestInit = {},
	options: SessionFetchOptions = {},
): Promise<Response> {
	const token = options.public ? null : auth.getToken();
	const headers = new Headers(init.headers);
	if (token) {
		headers.set("Authorization", `Bearer ${token}`);
	}

	const response = await fetch(url, { ...init, headers });

	if (response.status === 401 && token) {
		auth.clearSession();
		throw auth.expiredError();
	}
	return response;
}
